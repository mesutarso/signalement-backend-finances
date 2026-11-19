#!/usr/bin/env python3
"""Acceptance of invoice completion, fiscal review, duplicates and request-specific SMS on isolated PostgreSQL/Redis and synthetic OCR.

Run with the OCR virtualenv after ./mvnw clean verify. The private --runtime JSON contains
databaseUser, databasePassword, postgresContainer, redisPassword and adminPassword.
No provider, production import, notification or real payment is called.
"""
import argparse
import base64
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import threading
import time
import uuid
from urllib.parse import parse_qs, urlparse
import requests

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:18081'
PHOTO = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=')
REFERENCES = ['VALID-A', 'VALID-B', 'VALID-C', 'CONFORM', 'DOUBT', 'CONCURRENT', 'DUP-1', 'DUP-2', 'UI', 'UNREADABLE', 'MIXED', 'FISCAL', 'REPLAY', 'IDENTITY', 'NIF-ONLY', 'AUTO-DUP-1', 'AUTO-DUP-2', 'REPLACEMENT', 'REPLACEMENT2', 'CROSS-EARLY', 'CROSS-LATE', 'CROSS-LAST', 'REVERSE-LATE', 'OLDER-PHOTO']
OCR_CALLS = []
CHECKS = []
SMS_CALLS = []


class Source(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def reply(self, body, kind='application/json'):
        if not isinstance(body, bytes): body = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.endswith('/GetProblems'):
            query = parse_qs(parsed.query)
            page = int(query.get('PageNumber', ['1'])[0])
            size = int(query.get('PageSize', ['10'])[0])
            fixtures = [dict(uid=ref, title='Facture de recette ' + ref, probDesc='Achat synthétique pour validation',
                reportedDateTime=f'2026-11-11T10:{i:02}:00', invoiceProblemImages=[dict(uid=ref + '-photo')],
                name='Déclarant ' + ref, email='recette@example.invalid', phone=f'09900000{i:02}', uidUser='person-' + ref)
                for i, ref in enumerate(REFERENCES)]
            for fixture in fixtures:
                if fixture['uid'] in ['UNREADABLE','REPLACEMENT','REPLACEMENT2','OLDER-PHOTO']:
                    fixture['uidUser']='person-UNREADABLE'
                    fixture['phone']='0827243119'
                if fixture['uid']=='OLDER-PHOTO': fixture['reportedDateTime']='2026-11-11T10:08:30'
                if fixture['uid']=='REVERSE-EARLY': fixture['reportedDateTime']='2026-11-11T10:21:30'
                if fixture['uid'] == 'MIXED':
                    fixture['invoiceProblemImages'].append(dict(uid='MIXED-UNREADABLE-photo'))
            self.reply(dict(problems=fixtures[(page-1)*size:page*size], hasNext=page*size < len(fixtures)))
        elif '/GetImage/' in parsed.path:
            reference = parsed.path.rsplit('/', 1)[1].removesuffix('-photo')
            self.reply(PHOTO + ('AUTO-DUP-1' if reference == 'AUTO-DUP-2' else reference).encode(), 'image/png')
        else: self.send_error(404)
    def do_POST(self):
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b';')[0].strip(), 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            raw = b''.join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get('Content-Length', '0')))
        body = json.loads(raw)
        if self.path == '/sms':
            SMS_CALLS.append(body)
            self.reply({'accepted': True})
            return
        reference = base64.b64decode(body['image'])[len(PHOTO):].decode()
        OCR_CALLS.append(reference)
        if reference in ['UNREADABLE', 'MIXED-UNREADABLE']:
            self.reply(dict(vendeur=None, nif=None, date=None, montant=None, numero=None, code=None,
                articles=[], confiance=0.99))
            return
        invoice = 'REVERSE' if reference.startswith('REVERSE-') else 'CROSS' if reference.startswith('CROSS-') else 'DUP' if reference.startswith('DUP-') else reference
        self.reply(dict(vendeur=None if reference in ['IDENTITY','NIF-ONLY'] else 'Établissement de recette ' + invoice, nif=None if reference == 'IDENTITY' else 'NIF-' + invoice, date='2026-11-11' if reference == 'DUP-2' else '2026-11-10',
            montant='116000', numero='FACT-' + invoice, code=None if reference=='CROSS-LATE' else 'CODE-' + invoice, devise='CDF', montant_ht='100000',
            tva='0' if reference == 'REPLAY' and OCR_CALLS.count(reference) > 1 else '16000', taux_tva='16', montant_paye='116000', code_def='DEF-DEMO' if reference == 'UI' else None, numero_def='DEVICE-DEMO' if reference == 'UI' else None,
            articles=[dict(designation='Article synthétique', montant='100000')], confiance=0.99))


def check(label, value):
    if not value: raise AssertionError(label)
    CHECKS.append(label)
    print('OK', label, flush=True)


def call(session, method, path, body=None, expected=200):
    headers = {'X-XSRF-TOKEN': session.cookies.get('XSRF-TOKEN', '')} if method != 'GET' else {}
    response = session.request(method, BASE + path, json=body, headers=headers, timeout=30)
    if response.status_code != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {response.status_code}: {response.text[:500]}')
    return response.json() if response.content else None


def login(username, password):
    session = requests.Session()
    call(session, 'GET', '/api/auth/csrf', expected=204)
    call(session, 'POST', '/api/auth', dict(username=username, password=password))
    call(session, 'GET', '/api/auth/csrf', expected=204)
    return session


def until(function, timeout=90):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            value = function()
            if value: return value
        except (requests.RequestException, AssertionError): pass
        time.sleep(0.25)
    raise AssertionError('Timed out waiting for the isolated backend')


def run(cfg, sql):
    admin=login('validation-admin',cfg['adminPassword'])
    for role in ['AGENT','SUPERVISOR','AUDITOR']:
        call(admin,'POST','/api/accounts',dict(username='validation-'+role.lower(),password=cfg['adminPassword'],role=role),201)
    agent=login('validation-agent',cfg['adminPassword'])
    auditor=login('validation-auditor',cfg['adminPassword'])
    call(admin,'POST','/api/report-imports',dict(start='2026-11-01',end='2026-11-30'),202)
    reports=until(lambda:(data if (data:=call(admin,'GET','/api/reports?validation=all&size=100'))['total']==len(REFERENCES) else None))
    by_ref={r['identifier']:r for r in reports['reports']}
    def view(ref):return call(admin,'GET',f"/api/reports/{by_ref[ref]['id']}/assessment")
    until(lambda:all(view(ref)['assessment']['jobState']=='done' for ref in REFERENCES))
    def correct(ref,session=agent,**changes):
        a=view(ref)['assessment']
        payload={k:a[k] for k in ['revision','sellerName','sellerTaxId','documentNumber','documentDate','normalization']}
        payload.update(changes)
        return call(session,'POST',f"/api/reports/{by_ref[ref]['id']}/assessment",payload)
    def fiscal(ref,status,session=agent):
        a=view(ref)['assessment']
        return call(session,'POST',f"/api/reports/{by_ref[ref]['id']}/assessment/fiscal-status",
                    dict(revision=a['revision'],vatStatus=status,vatEvidence='Référentiel fiscal synthétique, contrôle du 19/11/2026'))
    check('Import and extraction finish without fiscal API access',len(OCR_CALLS)==len(REFERENCES)+1)
    check('Unknown fiscal status never earns a reward',all(view(ref)['reward'] is None for ref in REFERENCES))
    first_id=by_ref['CROSS-EARLY']['id']
    check('Different users reporting the same invoice keep the first submission',
          view('CROSS-EARLY')['assessment']['state']=='doubt'
          and all(view(ref)['assessment']['state']=='rejected' for ref in ['CROSS-LATE','CROSS-LAST']))
    tracked=call(agent,'GET',f"/api/reports/{by_ref['CROSS-LATE']['id']}")['duplicates']
    pair=next(item for item in tracked if item['otherReportId']==first_id and item['status']=='confirmed')
    check('Duplicate details track both declarants and the original priority',pair['differentDeclarants'] is True
          and pair['currentReporterName']=='Déclarant CROSS-LATE' and pair['otherReporterName']=='Déclarant CROSS-EARLY'
          and pair['priorityReportId']==first_id and not pair['currentKeepsPriority'])
    hidden=call(auditor,'GET',f"/api/reports/{by_ref['CROSS-LATE']['id']}")['duplicates']
    check('Auditors cannot read declarant identities through duplicate details',all(item['currentReporterName'] is None
          and item['currentReporterPhone'] is None and item['otherReporterName'] is None
          and item['otherReporterPhone'] is None and item['differentDeclarants'] is None for item in hidden))
    correct('CROSS-EARLY',normalization='non-normalized')
    first_reward=fiscal('CROSS-EARLY','registered')
    check('Only the earliest declarant earns a reward for the shared invoice',first_reward['reward'] is not None
          and all(view(ref)['reward'] is None for ref in ['CROSS-LATE','CROSS-LAST']))
    late=view('CROSS-LATE')['assessment']
    call(admin,'POST',f"/api/reports/{by_ref['CROSS-LATE']['id']}/assessment/fiscal-status",
         dict(revision=late['revision'],vatStatus='registered',vatEvidence='Test refus doublon'),409)

    correct('REVERSE-LATE',normalization='non-normalized')
    later_reward=fiscal('REVERSE-LATE','registered')['reward']
    check('A sole later-submitted invoice can initially receive an unpaid proposed reward',later_reward is not None)
    REFERENCES.append('REVERSE-EARLY')
    call(admin,'POST','/api/report-imports',dict(start='2026-11-01',end='2026-11-30'),202)
    def imported_earlier():
        data=call(admin,'GET','/api/reports?validation=all&size=100')
        return next((row for row in data['reports'] if row['identifier']=='REVERSE-EARLY'),None)
    early=until(imported_earlier);by_ref['REVERSE-EARLY']=early
    until(lambda:view('REVERSE-EARLY')['assessment']['jobState']=='done' and view('REVERSE-LATE')['assessment']['state']=='rejected')
    correct('REVERSE-EARLY',normalization='non-normalized')
    earlier_reward=fiscal('REVERSE-EARLY','registered')['reward']
    check('An earlier historical claim imported later takes priority and releases only the unpaid later reward',
          earlier_reward is not None and view('REVERSE-LATE')['reward']['status']=='withdrawn'
          and sql("select count(*) from app.prime where status<>'withdrawn' and report_id in ('"+early['id']+"','"+by_ref['REVERSE-LATE']['id']+"');")=='1')
    extracted=view('UI')['assessment']['extracted']
    check('Invoice number, amounts and DEF references are extracted',extracted['documentNumber']=='FACT-UI'
          and extracted['totalAmount']=='116000' and extracted['defCode']=='DEF-DEMO' and extracted['defNumber']=='DEVICE-DEMO')
    initial=view('UI')
    check('DEF extraction alone never proves normalization',initial['assessment']['normalization']=='unknown'
          and initial['precalculation']['ifRegistered']['state']=='doubt')
    check('Both fiscal forecasts are available before confirmation',initial['precalculation']['analysisComplete']
          and initial['precalculation']['fiscalStatusMissing'] and not initial['fiscalApiAvailable'])
    prepared=correct('VALID-A',normalization='non-normalized')
    check('Precalculation describes both fiscal branches',prepared['precalculation']['onlyFiscalStatusMissing']
          and prepared['precalculation']['ifRegistered']['state']=='validated' and prepared['assessment']['vatStatus']=='unknown')
    aid=by_ref['VALID-A']['id']
    call(agent,'POST',f'/api/reports/{aid}/assessment/fiscal-status',dict(revision=prepared['assessment']['revision'],vatStatus='registered'),400)
    call(auditor,'POST',f'/api/reports/{aid}/assessment/fiscal-status',dict(revision=prepared['assessment']['revision'],vatStatus='registered',vatEvidence='Test'),403)
    validated=fiscal('VALID-A','registered')
    check('Agent confirmation records its reference, author and date',validated['assessment']['checkedBy'] is not None
          and validated['assessment']['checkedAt'] and 'Référentiel fiscal' in validated['assessment']['vatEvidence'])
    check('Confirmed registered seller with non-normalized invoice earns one reward',validated['assessment']['state']=='validated'
          and validated['reward']['amount']==5000 and sql(f"select count(*) from app.prime where report_id='{aid}';")=='1')
    check('Fiscal submission preserves the invoice evidence',all(validated['assessment'][k]==prepared['assessment'][k]
          for k in ['sellerTaxId','documentNumber','documentDate','photoId','extracted','readings']))
    call(agent,'POST',f'/api/reports/{aid}/assessment',dict(revision=validated['assessment']['revision'],normalization='unknown'),403)
    unregistered=fiscal('VALID-C','unregistered')
    check('VAT charged by an unregistered seller is accepted',unregistered['assessment']['state']=='validated')
    correct('CONFORM',normalization='normalized')
    conform=fiscal('CONFORM','registered')
    check('A normalized invoice from a registered seller is not rewarded',conform['assessment']['state']=='rejected' and conform['reward'] is None)
    uid=by_ref['UNREADABLE']['id']; unreadable=view('UNREADABLE')
    check('Unreadable photo stays actionable and has no reward',unreadable['assessment']['state']=='doubt'
          and 'Photo lisible' in unreadable['precalculation']['missingFields'] and unreadable['reward'] is None)
    check('Missing NIF stays actionable with the exact missing field',view('IDENTITY')['assessment']['state']=='doubt'
          and 'NIF' in view('IDENTITY')['precalculation']['missingFields'])
    corrected=correct('IDENTITY',sellerTaxId='NIF-REPAIRED')
    check('An agent can correct a NIF absent from OCR',corrected['assessment']['sellerTaxId']=='NIF-REPAIRED'
          and 'NIF' not in corrected['precalculation']['missingFields'])
    request_path=f"/api/reports/{uid}/photos/{unreadable['assessment']['photoId']}/requests"
    call(auditor,'POST',request_path,dict(reason='NIF et numéro illisibles'),403)
    call(agent,'POST',request_path.replace(unreadable['assessment']['photoId'],view('UI')['assessment']['photoId']),dict(reason='Photo floue'),404)
    with ThreadPoolExecutor(2) as pool: requests=list(pool.map(lambda _:call(agent,'POST',request_path,dict(reason='NIF et numéro illisibles')),range(2)))
    request=requests[0];rid=request['id']
    check('Concurrent requests prepare one request and one unsent SMS',requests[0]['id']==requests[1]['id']
          and sql(f"select count(*) from app.declarant_message where photo_request_id='{rid}';")=='1' and not SMS_CALLS)
    delivery=f'/api/reports/{uid}/photo-requests/{rid}/deliveries'
    call(auditor,'POST',delivery,expected=403)
    call(agent,'POST',delivery.replace(uid,by_ref['UI']['id']),expected=409)
    sent=call(agent,'POST',delivery); call(agent,'POST',delivery,expected=409)
    check('Agent sends the prepared SMS once to the stored declarant number',sent['status']=='accepted' and len(SMS_CALLS)==1
          and SMS_CALLS[0]['tel']=='243827243119' and request['referenceCode'] in SMS_CALLS[0]['message'] and len(SMS_CALLS[0]['message'])<=100)
    link=f'/api/reports/{uid}/photo-requests/{rid}/links'
    call(agent,'POST',link,dict(referenceCode='UI'),409)
    call(agent,'POST',link,dict(referenceCode='OLDER-PHOTO'),409)
    check('An older submission cannot be relabeled as a later replacement',True)
    check('Another declarant cannot supply the replacement',sql(f"select status from app.photo_request where id='{rid}';")=='open')
    linked=call(agent,'POST',link,dict(referenceCode='REPLACEMENT'))
    until(lambda:view('UNREADABLE')['assessment']['jobState']=='done')
    restored=view('UNREADABLE')
    check('Replacement is extracted into the original dossier',restored['assessment']['sellerTaxId']=='NIF-REPLACEMENT'
          and restored['assessment']['state']=='doubt' and not restored['precalculation']['missingFields'])
    check('Receipt closes the request while retaining its evidence',linked['status']=='linked'
          and sql(f"select resolved_at is not null from app.photo_request where id='{rid}';")=='t'
          and len(call(agent,'GET',f'/api/reports/{uid}')['photos'])==2)
    check('The replacement report is excluded from a second reward',view('REPLACEMENT')['assessment']['state']=='rejected'
          and view('REPLACEMENT')['reward'] is None)
    check('Completed request leaves the active reminder queue',uid not in [r['id'] for r in call(agent,'GET','/api/reports?photoRequest=open&size=100')['reports']])
    call(agent,'POST',delivery,expected=409)
    again=call(agent,'POST',f"/api/reports/{uid}/photos/{restored['assessment']['photoId']}/requests",dict(reason='Vérifier le bas de la facture'))
    check('A later request gets a separate unsent SMS',again['id']!=rid and again['messageStatus']=='prepared')
    second_link=f"/api/reports/{uid}/photo-requests/{again['id']}/links"
    call(agent,'POST',second_link,dict(referenceCode='REPLACEMENT2'))
    until(lambda:view('UNREADABLE')['assessment']['jobState']=='done')
    twice=view('UNREADABLE')
    check('A second replacement selects the newest request rather than an older readable photo',
          twice['assessment']['sellerTaxId']=='NIF-REPLACEMENT2' and twice['assessment']['documentNumber']=='FACT-REPLACEMENT2')
    third=call(agent,'POST',f"/api/reports/{uid}/photos/{twice['assessment']['photoId']}/requests",dict(reason='Dernière relance de recette'))
    call(admin,'POST','/api/declarant-messages',dict(reference='UNREADABLE',kind='photo'),400)
    old=str(uuid.uuid4())
    sql(f"insert into app.declarant_message(id,report_id,report_reference,kind,body,status,prepared_by,prepared_at) values('{old}','{uid}','UNREADABLE','photo','Ancien brouillon','prepared','{uuid.uuid4()}',now());")
    call(admin,'POST',f'/api/declarant-messages/{old}/deliveries',expected=409)
    check('Unlinked old photo drafts stay unsent',len(SMS_CALLS)==1)
    check('Exact duplicates retain one actionable dossier',view('AUTO-DUP-1')['assessment']['state']=='doubt'
          and view('AUTO-DUP-2')['assessment']['state']=='rejected')
    duplicate=call(agent,'GET',f"/api/reports/{by_ref['DUP-1']['id']}")['duplicates'][0]
    call(agent,'POST',f"/api/reports/{by_ref['DUP-1']['id']}/duplicates/{duplicate['id']}",dict(decision='confirm',reason='Même facture vérifiée en recette'))
    until(lambda:all(view(ref)['assessment']['jobState']=='done' for ref in ['DUP-1','DUP-2']))
    check('Agent can resolve a suspected duplicate',sum(view(ref)['assessment']['state']=='rejected' for ref in ['DUP-1','DUP-2'])==1)
    filtered=call(agent,'GET','/api/reports?q=NIF-REPAIRED&size=1')
    check('Global NIF search agrees with pagination',filtered['total']==1 and filtered['reports'][0]['identifier']=='IDENTITY')
    check('Invoice search is available',any(r['identifier']=='IDENTITY' for r in call(agent,'GET','/api/reports?q=FACT-IDENTITY')['reports']))
    check('Period filters restrict all results',call(agent,'GET','/api/reports?start=2026-11-12')['total']==0)
    check('Oldest-first ordering is applied before pagination',call(agent,'GET','/api/reports?validation=all&sort=oldest&size=1')['reports'][0]['identifier']=='VALID-A')
    call(agent,'GET','/api/reports?start=2026-11-30&end=2026-11-01',expected=400)
    call(agent,'GET','/api/reports?sort=unknown',expected=400)
    check('VAT and reading filters are applied on the server',all(r['work']['vatStatus']=='unknown' and r['work']['jobState']=='done'
          for r in call(agent,'GET','/api/reports?vat=unknown&reading=done')['reports']))
    check('Reminder filter includes active requests',uid in [r['id'] for r in call(agent,'GET','/api/reports?photoRequest=open')['reports']])
    check('Agents can select an agent filter from the directory',any(a['username']=='validation-agent' for a in call(agent,'GET','/api/reports/assignees')))
    check('Duplicate status filter includes excluded submissions',all(r['screeningReason']=='duplicate'
          for r in call(agent,'GET','/api/reports?validation=rejected-duplicate')['reports']))
    race=view('CONCURRENT')['assessment'];raceid=by_ref['CONCURRENT']['id']
    payload={k:race[k] for k in ['revision','sellerName','sellerTaxId','documentNumber','documentDate','normalization']}
    clients=[login('validation-agent',cfg['adminPassword']),login('validation-supervisor',cfg['adminPassword'])]
    def write(client):return client.post(BASE+f'/api/reports/{raceid}/assessment',json=payload,headers={'X-XSRF-TOKEN':client.cookies.get('XSRF-TOKEN','')},timeout=30).status_code
    with ThreadPoolExecutor(2) as pool: codes=list(pool.map(write,clients))
    check('Concurrent corrections reject the stale revision',sorted(codes)==[200,409])
    cfg.update(api=BASE,username='validation-admin',agentUsername='validation-agent',reportId=by_ref['UI']['id'],unreadableId=uid)

def main():
    global BASE
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--keep-running', action='store_true')
    parser.add_argument('--port', type=int, default=18081)
    args = parser.parse_args()
    BASE = f'http://127.0.0.1:{args.port}'
    cfg = json.loads(args.runtime.read_text())
    database = 'validation_acceptance_' + uuid.uuid4().hex[:12]
    def sql(statement, db=None):
        result = subprocess.run(['docker','exec','-i',cfg['postgresContainer'],'psql','-U',cfg['databaseUser'],'-d',db or database,'-v','ON_ERROR_STOP=1','-At'],
            input=statement, text=True, capture_output=True)
        if result.returncode: raise RuntimeError(result.stderr[-1000:])
        return result.stdout.strip()
    until(lambda: subprocess.run(['docker', 'exec', cfg['postgresContainer'], 'pg_isready', '-U', cfg['databaseUser'], '-d', cfg['database']], capture_output=True).returncode == 0)
    sql('create database "' + database + '";', cfg['database'])
    cfg['recetteDatabase'] = database
    cfg['replayBefore'] = datetime.now(timezone.utc).isoformat()
    source = ThreadingHTTPServer(('127.0.0.1',0), Source)
    threading.Thread(target=source.serve_forever, daemon=True).start()
    env = dict(os.environ, SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:65434/' + database,
        SPRING_DATASOURCE_USERNAME=cfg['databaseUser'], SPRING_DATASOURCE_PASSWORD=cfg['databasePassword'],
        SPRING_DATA_REDIS_HOST='127.0.0.1', SPRING_DATA_REDIS_PORT='65435', SPRING_DATA_REDIS_PASSWORD=cfg['redisPassword'],
        SPRING_SESSION_REDIS_NAMESPACE=database, SERVER_PORT=str(args.port), ACCESS_ADMIN_USERNAME='validation-admin', ACCESS_ADMIN_PASSWORD=cfg['adminPassword'],
        APP_FRONTEND_ORIGIN='http://localhost:3301', REPORTS_SCHEDULE_ENABLED='false', REPORTS_IMPORT_ON_STARTUP='false',
        REPORT_ANALYSIS_ENABLED='true', REPORT_ANALYSIS_REPLAY_BEFORE=cfg['replayBefore'], APP_VALIDATION_REPLAY_DELAY='500', APP_VALIDATION_ANALYSIS_DELAY='50', FACNO_BASE_URL=f'http://127.0.0.1:{source.server_port}/facno', FACNO_API_KEY='synthetic',
        UNIKRON_ENDPOINT=f'http://127.0.0.1:{source.server_port}/sms', UNIKRON_USER_NAME='synthetic', UNIKRON_APP_KEY='synthetic', UNIKRON_MODE='development',
        HERDYS_OCR_BASE_URL=f'http://127.0.0.1:{source.server_port}/ocr', HERDYS_OCR_API_TOKEN='synthetic')
    log_path = args.runtime.parent / 'backend.log'
    with log_path.open('w') as log:
        backend = subprocess.Popen(['java','-jar',str(ROOT/'target/signalements-0.0.1-SNAPSHOT.jar')], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            until(lambda: requests.get(BASE + '/actuator/health', timeout=2).status_code == 200)
            run(cfg, sql)
            args.runtime.write_text(json.dumps(cfg))
            (args.runtime.parent/'completion-result.json').write_text(json.dumps(dict(checks=CHECKS, passed=len(CHECKS), database=database), ensure_ascii=False, indent=2))
            print(f'RECETTE OK: {len(CHECKS)} contrôles, base isolée {database}', flush=True)
            if args.keep_running:
                print('Recette conservée pour vérification navigateur.', flush=True)
                threading.Event().wait()
        finally:
            backend.terminate()
            backend.wait(timeout=30)
            source.shutdown()


if __name__ == '__main__': main()

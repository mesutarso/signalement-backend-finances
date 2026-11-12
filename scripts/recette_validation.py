#!/usr/bin/env python3
"""Acceptance of the five-criterion workflow on isolated PostgreSQL/Redis and synthetic OCR.

Run with the OCR virtualenv after ./mvnw clean verify. The private --runtime JSON contains
databaseUser, databasePassword, postgresContainer, redisPassword and adminPassword.
No provider, production import, notification or real payment is called.
"""
import argparse
import base64
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
REFERENCES = ['VALID-A', 'VALID-B', 'VALID-C', 'CONFORM', 'DOUBT', 'CONCURRENT', 'DUP-1', 'DUP-2', 'UI']
OCR_CALLS = []
CHECKS = []


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
                name='Déclarant de recette', email='recette@example.invalid', phone=f'09900000{i:02}', uidUser='person-' + ref)
                for i, ref in enumerate(REFERENCES)]
            self.reply(dict(problems=fixtures[(page-1)*size:page*size], hasNext=page*size < len(fixtures)))
        elif '/GetImage/' in parsed.path:
            reference = parsed.path.rsplit('/', 1)[1].removesuffix('-photo')
            self.reply(PHOTO + reference.encode(), 'image/png')
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
        reference = base64.b64decode(body['image'])[len(PHOTO):].decode()
        OCR_CALLS.append(reference)
        invoice = 'DUP' if reference.startswith('DUP-') else reference
        self.reply(dict(vendeur='Établissement de recette ' + invoice, nif='NIF-' + invoice, date='2026-11-10',
            montant='116000', numero='FACT-' + invoice, code='CODE-' + invoice, devise='CDF', montant_ht='100000',
            tva='16000', taux_tva='16', montant_paye='116000', code_def=None, numero_def=None,
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
    password = cfg['adminPassword']
    admin = login('validation-admin', password)
    for role in ['AGENT', 'SUPERVISOR', 'AUDITOR', 'CELLULE', 'CABINET', 'PAYMASTER']:
        call(admin, 'POST', '/api/accounts', dict(username='validation-' + role.lower(), password=password, role=role), 201)
    agent = login('validation-agent', password)
    supervisor = login('validation-supervisor', password)
    auditor = login('validation-auditor', password)
    call(admin, 'POST', '/api/report-imports', dict(start='2026-11-01', end='2026-11-30'), 202)
    reports = until(lambda: (data if (data := call(admin, 'GET', '/api/reports?size=100'))['total'] == len(REFERENCES) else None))
    by_ref = {r['identifier']: r for r in reports['reports']}
    until(lambda: all(r['validationState'] == 'doubt' for r in call(admin, 'GET', '/api/reports?size=100')['reports']))
    check('Automatic extraction completes without inventing fiscal verification', len(OCR_CALLS) == len(REFERENCES))
    legacy_report, legacy_prime, legacy_actor = (str(uuid.uuid4()) for _ in range(3))
    sql(f"insert into app.report (id,external_id,source,reference_code,reported_on,reported_at,title,description,imported_at,reporter_user_id,validation_state) values ('{legacy_report}','LEGACY','test','LEGACY',current_date,now(),'Ancien dossier de recette','Données synthétiques',now(),'legacy-person','legacy');")
    sql(f"insert into app.point_movement (id,report_id,declarant_id,tax_id,finding,base_points,bonus_points,kind,rule_version,dgi_reference,filed_on,actor_id,recorded_at) values ('{uuid.uuid4()}','{legacy_report}','legacy-person','OLD-NIF','non-normalized',30,0,'credit','cdc-2026-10-28','OLD-REF',current_date,'{legacy_actor}',now());")
    sql(f"insert into app.prime (id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at) values ('{legacy_prime}','legacy-person','0999999999','cdc-2026-10-28',30,10000,'approved','{legacy_actor}',now(),now());")
    for path in ['/api/points','/api/primes','/api/rules',f'/api/reports/{legacy_report}/points',f'/api/reports/{legacy_report}/prime']:
        call(admin,'GET',path,expected=404)
    call(admin,'POST',f'/api/rewards/{legacy_prime}/reviews',dict(result='approved'),404)
    old_situation = call(admin,'GET','/api/declarants?reference=LEGACY')
    check('Retired points and tier rewards have no API history or approval path', 'points' not in old_situation and old_situation['rewardAmount'] == 0 and all('points' not in r for r in old_situation['reports']))
    figures = call(admin,'GET','/api/indicators')['figures']
    check('Current indicators exclude historical tier rewards and point balances', 'points' not in figures and figures['rewards'] == 0)
    check('No reward exists before a proven validation', call(admin, 'GET', '/api/rewards')['total'] == 0)
    check('Agent filters cannot reveal automatically closed or historical dossiers', all(r['validationState'] == 'doubt' for r in call(agent, 'GET', '/api/reports?validation=validated')['reports']))

    def view(ref): return call(admin, 'GET', f"/api/reports/{by_ref[ref]['id']}/assessment")
    def body(ref, **changes):
        a = view(ref)['assessment']
        data = {key: a.get(key) for key in ['revision','photoId','sellerName','sellerTaxId','documentNumber','documentDate','vatStatus','vatEvidence',
            'normalization','normalizationEvidence','purchaseConformity','purchaseEvidence','vatCharged','vatChargeEvidence','uniqueness','uniquenessEvidence']}
        data.update(vatStatus='registered', vatEvidence='Référentiel fiscal synthétique, contrôle à la date de la facture',
            normalization='non-normalized', normalizationEvidence='Mentions de la pièce contrôlées en recette',
            uniqueness='unique', uniquenessEvidence='Recherche des références dans les dossiers de recette')
        data.update(changes)
        return data
    def review(ref, session=agent, data=None, **changes):
        return call(session, 'POST', f"/api/reports/{by_ref[ref]['id']}/assessment", data or body(ref, **changes))

    a_id = by_ref['VALID-A']['id']
    invalid = body('VALID-A', vatEvidence=None)
    call(agent, 'POST', f'/api/reports/{a_id}/assessment', invalid, 400)
    check('A fiscal claim without its evidence is refused', view('VALID-A')['reward'] is None)
    first_body = body('VALID-A')
    first = review('VALID-A', data=first_body)
    check('Registered seller and non-normalized invoice earn exactly 5000 FC', first['assessment']['state'] == 'validated' and first['reward']['amount'] == 5000)
    photo_id = first['assessment']['photoId']
    call(agent, 'GET', f'/api/reports/{a_id}', expected=403)
    call(agent, 'GET', f'/api/reports/{a_id}/photos/{photo_id}', expected=403)
    call(agent, 'POST', f'/api/reports/{a_id}/assessment', first_body, 403)
    call(admin, 'POST', f'/api/reports/{a_id}/assessment', first_body, 409)
    for path in ['/api/indicators', '/api/seller-files', '/api/rewards']:
        call(agent, 'GET', path, expected=403)
    check('Agent access is restricted on the server, including direct attachment links', a_id not in [r['id'] for r in call(agent, 'GET', '/api/reports')['reports']])
    call(admin, 'POST', f'/api/reports/{a_id}/qualifications', dict(decision='confirm', finding='non-normalized', proof='photo', justification='Ancienne grille'), 404)
    call(admin, 'POST', f'/api/reports/{a_id}/points', dict(reference='TEST'), 404)
    check('The retired grid and point credit cannot bypass the new workflow', True)

    current = call(admin, 'GET', '/api/reward-settings')
    call(agent, 'POST', '/api/reward-settings', dict(amount=7000, expectedId=current['id']), 403)
    call(admin, 'POST', '/api/reward-settings', dict(amount=7000, expectedId=str(uuid.uuid4())), 409)
    call(admin, 'POST', '/api/reward-settings', dict(amount=7000, expectedId=current['id']))
    b = review('VALID-B', normalization='normalized', normalizationEvidence='Facture normalisée de recette',
        purchaseConformity='nonconform', purchaseEvidence='Justificatif synthétique de l’achat comparé')
    c = review('VALID-C', vatStatus='unregistered', normalization='unknown', normalizationEvidence=None)
    check('Actual purchase mismatch and VAT charged by an unregistered seller are accepted', b['assessment']['state'] == c['assessment']['state'] == 'validated')
    check('The configurable amount applies only to new rewards', b['reward']['amount'] == c['reward']['amount'] == 7000 and view('VALID-A')['reward']['amount'] == 5000)
    replay = review('VALID-A', session=admin)
    check('Revalidation preserves the original reward ID and amount', replay['reward']['id'] == first['reward']['id'] and replay['reward']['amount'] == 5000)
    conform = review('CONFORM', normalization='normalized', normalizationEvidence='Pièce vérifiée', purchaseConformity='conform', purchaseEvidence='Achat réel vérifié')
    unknown = review('DOUBT', vatStatus='unknown', vatEvidence=None)
    check('A conforming invoice is rejected and unknown fiscal status remains doubtful', conform['assessment']['state'] == 'rejected' and unknown['assessment']['state'] == 'doubt' and unknown['reward'] is None)

    race_body = body('CONCURRENT')
    race_id = by_ref['CONCURRENT']['id']
    clients = [login('validation-admin', password), login('validation-supervisor', password)]
    def concurrent(client):
        return client.post(BASE + f'/api/reports/{race_id}/assessment', json=race_body,
            headers={'X-XSRF-TOKEN': client.cookies.get('XSRF-TOKEN', '')}, timeout=30).status_code
    with ThreadPoolExecutor(2) as executor: codes = list(executor.map(concurrent, clients))
    check('Concurrent reviews create one reward and reject the stale decision', sorted(codes) == [200, 409])

    dup_id = by_ref['DUP-1']['id']
    duplicate = call(admin, 'GET', f'/api/reports/{dup_id}')['duplicates'][0]
    call(admin, 'POST', f"/api/reports/{dup_id}/duplicates/{duplicate['id']}", dict(decision='confirm', reason='Même facture synthétique'))
    until(lambda: all(view(ref)['assessment']['jobState'] == 'done' for ref in ['DUP-1','DUP-2']))
    winner = call(admin, 'GET', f'/api/reports/{dup_id}')['duplicates'][0]['priorityReportId']
    win_ref = next(ref for ref in ['DUP-1','DUP-2'] if by_ref[ref]['id'] == winner)
    lose_ref = next(ref for ref in ['DUP-1','DUP-2'] if by_ref[ref]['id'] != winner)
    win = review(win_ref)
    lose = view(lose_ref)
    check('A confirmed duplicate can never earn a second reward', win['reward'] is not None and lose['assessment']['state'] == 'rejected' and lose['reward'] is None)

    identity = view('VALID-C')['assessment']
    collision = {key: identity[key] for key in ['sellerName','sellerTaxId','documentNumber','documentDate']}
    collision.update(vatStatus='unregistered', vatEvidence='Vérification fiscale synthétique', vatCharged='yes', vatChargeEvidence='TVA visible sur la facture')
    held = review('CONFORM', session=admin, **collision)
    check('A rewarded invoice reference is held for review rather than rewarded twice', held['assessment']['state'] == 'doubt' and held['reward'] is None)
    distinct = review('CONFORM', session=admin, **dict(collision, documentDate='2026-11-11'))
    check('Distinct invoice dates do not falsely reject a reused invoice number', distinct['assessment']['state'] == 'validated' and distinct['reward'] is not None)

    for ref in ['VALID-A','VALID-B']:
        call(admin, 'POST', '/api/beneficiaries', dict(reference=ref, beneficiaryReference='TEST-PERSON-' + ref, evidence='JUSTIFICATIF-SYNTHETIQUE'))
        call(supervisor, 'POST', f"/api/rewards/{view(ref)['reward']['id']}/reviews", dict(result='approved'))
    check('Fixed rewards remain compatible with identity checks and payment approval', view('VALID-A')['reward']['status'] == 'approved')
    sql("update app.payout_number set lost_at=now() where declarant_id in ('person-VALID-A','person-VALID-B');")
    statement = call(admin, 'POST', '/api/payment-statements')
    check('Historical point forfeiture does not remove newly approved fixed rewards', len(statement['lines']) == 2)
    call(admin, 'POST', '/api/corrections', dict(reference='VALID-A', reason='Correction de recette : confirmation infirmée'))
    call(supervisor, 'POST', f"/api/payment-statements/{statement['id']}/controls", dict(result='accept'), 409)
    check('Correcting a decision withdraws the unpaid reward and blocks an existing payment statement', view('VALID-A')['reward']['status'] == 'withdrawn')
    call(supervisor, 'POST', f"/api/payment-statements/{statement['id']}/controls", dict(result='refuse', reason='État à refaire après correction'))
    statement = call(admin, 'POST', '/api/payment-statements')
    call(supervisor, 'POST', f"/api/payment-statements/{statement['id']}/controls", dict(result='accept'))
    cellule = login('validation-cellule', password)
    cabinet = login('validation-cabinet', password)
    paymaster = login('validation-paymaster', password)
    call(cellule, 'POST', f"/api/payment-statements/{statement['id']}/visas", dict(result='accept'))
    call(cabinet, 'POST', f"/api/payment-statements/{statement['id']}/authorizations", dict(result='accept'))
    call(paymaster, 'POST', f"/api/payment-statements/{statement['id']}/attempts", dict(lineId=statement['lines'][0]['id'], operator='m-pesa', result='paid'), 410)
    call(admin, 'POST', '/api/corrections', dict(reference='VALID-B', reason='Correction sans paiement confirmé'))
    check('The retired simulation cannot protect an unpaid reward from correction', view('VALID-B')['reward']['status'] == 'withdrawn')
    check('No points were written by any new validation', sql(f"select count(*) from app.point_movement where report_id <> '{legacy_report}';") == '0')
    check('Assessment and reward settings expose only the current state', 'history' not in view('VALID-A') and 'assignments' not in call(admin, 'GET', f'/api/reports/{a_id}') and 'qualifications' not in call(admin, 'GET', f'/api/reports/{a_id}') and isinstance(call(admin, 'GET', '/api/reward-settings'), dict))
    saved = json.loads(sql(f"select evidence::text from app.assessment_event where report_id='{a_id}' and decision='validated' order by revision desc limit 1;"))
    check('Decision proofs remain traceable internally', saved['vatEvidence'] and saved['documentDate'] == '2026-11-10' and len(saved['readings']) == 1)
    cfg.update(api=BASE, username='validation-admin', agentUsername='validation-agent', reportId=by_ref['UI']['id'])


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
    source = ThreadingHTTPServer(('127.0.0.1',0), Source)
    threading.Thread(target=source.serve_forever, daemon=True).start()
    env = dict(os.environ, SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:65434/' + database,
        SPRING_DATASOURCE_USERNAME=cfg['databaseUser'], SPRING_DATASOURCE_PASSWORD=cfg['databasePassword'],
        SPRING_DATA_REDIS_HOST='127.0.0.1', SPRING_DATA_REDIS_PORT='65435', SPRING_DATA_REDIS_PASSWORD=cfg['redisPassword'],
        SPRING_SESSION_REDIS_NAMESPACE=database, SERVER_PORT=str(args.port), ACCESS_ADMIN_USERNAME='validation-admin', ACCESS_ADMIN_PASSWORD=cfg['adminPassword'],
        APP_FRONTEND_ORIGIN='http://localhost:3301', REPORTS_SCHEDULE_ENABLED='false', REPORTS_IMPORT_ON_STARTUP='false',
        REPORT_ANALYSIS_ENABLED='true', FACNO_BASE_URL=f'http://127.0.0.1:{source.server_port}/facno', FACNO_API_KEY='synthetic',
        HERDYS_OCR_BASE_URL=f'http://127.0.0.1:{source.server_port}/ocr', HERDYS_OCR_API_TOKEN='synthetic')
    log_path = args.runtime.parent / 'backend.log'
    with log_path.open('w') as log:
        backend = subprocess.Popen(['java','-jar',str(ROOT/'target/signalements-0.0.1-SNAPSHOT.jar')], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            until(lambda: requests.get(BASE + '/actuator/health', timeout=2).status_code == 200)
            run(cfg, sql)
            args.runtime.write_text(json.dumps(cfg))
            (args.runtime.parent/'acceptance-result.json').write_text(json.dumps(dict(checks=CHECKS, passed=len(CHECKS), database=database), ensure_ascii=False, indent=2))
            print(f'RECETTE OK: {len(CHECKS)} contrôles, base isolée {database}', flush=True)
            if args.keep_running:
                print('Recette conservée pour vérification navigateur.', flush=True)
                threading.Event().wait()
        finally:
            backend.terminate()
            backend.wait(timeout=30)
            source.shutdown()


if __name__ == '__main__': main()

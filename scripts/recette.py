#!/usr/bin/env python3
"""Repeatable HTTP acceptance test using an isolated database and synthetic FACNO/OCR fixtures.

Run after ./mvnw clean verify:
  ../ocr-flask-coolify/.venv/bin/python scripts/recette.py --keep-running
No real notification or payment is sent. The created database is retained for inspection.
"""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import subprocess
import threading
import time
import uuid
from urllib.parse import urlparse, parse_qs

import requests
from dotenv import dotenv_values

ROOT = Path(__file__).resolve().parents[1]
OUT = Path('/tmp/tombola-recette')
OUT.mkdir(exist_ok=True)
CHECKS = []
BASE = 'http://localhost:8081'
PHOTO = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=')
OCR = json.loads((ROOT / 'src/test/resources/ocr/herdys-response.json').read_text())
FIXTURES = []
for person in range(3):
    for index in range(10):
        uid = f'RECETTE-{person}-{index:02}'
        FIXTURES.append(dict(uid=uid, title='Facture de recette', probDesc='Document de test synthétique',
            reportedDateTime=f'2026-11-{1 + index // 4:02}T10:{index:02}:00',
            invoiceProblemImages=[dict(uid=uid + '-photo')], name='Bénéficiaire de recette',
            email='recette@example.invalid', phone=f'099000000{person}', uidUser=f'recette-person-{person}'))
FIXTURES.append(dict(FIXTURES[0], uid='RECETTE-REFUS', reportedDateTime='2026-11-04T10:00:00',
    invoiceProblemImages=[dict(uid='RECETTE-REFUS-photo')]))


class Source(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def reply(self, body, content_type='application/json'):
        if not isinstance(body, bytes):
            body = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path.endswith('/GetProblems'):
            params = parse_qs(parsed.query)
            page = int(params.get('PageNumber', ['1'])[0])
            size = int(params.get('PageSize', ['100'])[0])
            self.reply(dict(problems=FIXTURES[(page - 1) * size:page * size], hasNext=page * size < len(FIXTURES)))
        elif '/GetImage/' in parsed.path:
            self.reply(PHOTO + parsed.path.encode(), 'image/png')
        elif '/GetProblem/' in parsed.path:
            self.reply(next(item for item in FIXTURES if item['uid'] == parsed.path.rsplit('/', 1)[1]))
        else:
            self.send_error(404)

    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length', '0')))
        if self.path == '/ocr':
            self.reply(OCR)
        else:
            self.send_error(404)


def check(label, condition):
    if not condition:
        raise AssertionError(label)
    CHECKS.append(label)
    print('OK', label, flush=True)


def sql(statement, database):
    command = ['docker', 'compose', '--env-file', '/dev/null', 'exec', '-T', 'postgres', 'sh', '-c',
        'exec psql -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -At', 'psql', database]
    environment = dict(os.environ, **{k: v for k, v in dotenv_values(ROOT / '.env').items() if v is not None})
    result = subprocess.run(command, input=statement, text=True, cwd=ROOT, env=environment, capture_output=True)
    if result.returncode:
        raise RuntimeError('PostgreSQL recipe command failed: ' + result.stderr[-1000:])
    return result.stdout.strip()


def call(session, method, path, body=None, expected=200):
    headers = {'X-XSRF-TOKEN': session.cookies.get('XSRF-TOKEN', '')} if method != 'GET' else {}
    result = session.request(method, BASE + path, json=body, headers=headers, timeout=30)
    if result.status_code != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {result.status_code}: {result.text[:300]}')
    return result.json() if result.content else None


def login(username, password):
    session = requests.Session()
    call(session, 'GET', '/api/auth/csrf', expected=204)
    call(session, 'POST', '/api/auth', dict(username=username, password=password))
    return session


def wait_for(predicate, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = predicate()
            if result:
                return result
        except requests.RequestException:
            pass
        time.sleep(.3)
    raise TimeoutError('Acceptance server or asynchronous import did not finish')


def run(database, password):
    admin = login('recette-admin', password)
    actors = {}
    for role in ['AGENT', 'SUPERVISOR', 'AUDITOR', 'CELLULE', 'CABINET', 'PAYMASTER']:
        username = 'recette-' + role.lower()
        actor = call(admin, 'POST', '/api/accounts', dict(username=username, password=password, role=role), 201)
        actors[role] = (login(username, password), actor['id'])
    agent, agent_id = actors['AGENT']
    supervisor, _ = actors['SUPERVISOR']
    auditor, _ = actors['AUDITOR']

    def import_once():
        result = call(admin, 'POST', '/api/report-imports', dict(start='2026-11-01', end='2026-11-10'), 202)
        return wait_for(lambda: next((run for run in call(admin, 'GET', '/api/report-imports')['runs']
            if run['id'] == result['id'] and run['status'] in ['completed', 'failed']), None))

    imported = import_once()
    check('Import de 31 signalements synthétiques et de leurs photos', imported['status'] == 'completed' and imported['createdReports'] == 31 and imported['createdPhotos'] == 31)
    replay = import_once()
    check('Réimport sans second signalement ni seconde photo', replay['createdReports'] == 0 and replay['createdPhotos'] == 0)
    reports = call(admin, 'GET', '/api/reports?size=100')['reports']
    indexed = {report['identifier']: report for report in reports}
    first = indexed['RECETTE-0-00']
    body = dict(assigneeId=agent_id, priority='high', version=first['version'])
    call(admin, 'PUT', f"/api/reports/{first['id']}/assignment", body)
    detail = call(agent, 'GET', f"/api/reports/{first['id']}")
    check('Affectation et consultation par l’agent', detail['assigneeId'] == agent_id)
    photo = detail['photos'][0]
    read = call(agent, 'POST', f"/api/reports/{first['id']}/photos/{photo['id']}/readings")
    check('Lecture OCR sur contrat synthétique', read.get('status') == 'READ' and read.get('confidence') == .86)
    hidden = call(auditor, 'GET', f"/api/reports/{first['id']}")
    check('Identité du déclarant masquée à l’auditeur', hidden.get('reporterName') is None and hidden.get('reporterPhone') is None)
    call(auditor, 'POST', '/api/letter-routes', dict(taxId='TEST', service='DGI'), 403)
    check('Écritures interdites à l’auditeur', True)

    for item in FIXTURES:
        report = indexed[item['uid']]
        rid = report['id']
        refusal = item['uid'] == 'RECETTE-REFUS'
        qualification = dict(decision='wait' if refusal else 'confirm', finding='refusal' if refusal else 'non-normalized',
            proof='pending' if refusal else 'photo', justification='Examen de la pièce synthétique', sellerName='Vendeur synthétique',
            sellerTaxId='NIF-' + item['uid'], issuedOn='2026-11-01', amount='1000',
            documentNumber=None if refusal else 'FACT-' + item['uid'], missingElements='Confirmation DGI' if refusal else None)
        call(agent, 'POST', f'/api/reports/{rid}/qualifications', qualification)
        call(supervisor, 'POST', '/api/letter-routes', dict(taxId=qualification['sellerTaxId'], service='DGI-A' if 'RECETTE-0-' in item['uid'] or refusal else 'DGI-B'))

    wait_for(lambda: call(supervisor, 'GET', '/api/letters')['total'] == 2, seconds=10)
    check('Préparation planifiée des courriers sous un compte habilité', True)
    letters = call(supervisor, 'POST', '/api/letters/weekly')
    check('Un courrier distinct par service, refus en attente inclus', len(letters) == 2 and any(
        line['reportReference'] == 'RECETTE-REFUS' for lid in letters for line in call(supervisor, 'GET', f'/api/letters/{lid}')['lines']))
    check('Préparation hebdomadaire sans doublon', set(call(supervisor, 'POST', '/api/letters/weekly')) == set(letters))
    for lid in letters:
        info = call(supervisor, 'GET', f'/api/letters/{lid}/dispatches')
        call(supervisor, 'POST', f'/api/letters/{lid}/decisions', dict(result='validated', recipient=info['service'], reason=''))
        call(supervisor, 'POST', f'/api/letters/{lid}/dispatches', dict(sentOn='2026-11-10', reference='BORDEREAU-' + info['service']))
        annex = supervisor.get(BASE + f'/api/letters/{lid}/annexes?annex=company', timeout=10)
        check('Annexe contribuables sans identité du déclarant ' + info['service'], annex.ok and 'recette@example.invalid' not in annex.text and '099000000' not in annex.text)

    refusal = indexed['RECETTE-REFUS']
    rid = refusal['id']
    call(supervisor, 'POST', f'/api/reports/{rid}/points', dict(reference='DGI-REFUS'), 409)
    confirmed = dict(decision='confirm', finding='refusal', proof='authority', justification='Refus confirmé par DGI', dgiReference='DGI-REFUS')
    call(agent, 'POST', f'/api/reports/{rid}/qualifications', confirmed)
    call(agent, 'POST', f'/api/reports/{rid}/qualifications', confirmed)
    after = call(agent, 'GET', f'/api/reports/{rid}')
    check('Confirmation DGI rejouable avec conservation du NIF', len(after['qualifications']) == 2 and after['qualifications'][0]['sellerTaxId'] == 'NIF-RECETTE-REFUS')
    rule = dict(code='recette-nouvelle-version', fakeInvoice=9, missingTransmission=9, understated=9,
        nonNormalized=9, refusal=9, bonus=0, ceiling=1000000, dailyCap=5, spacingDays=3,
        tiers=[dict(threshold=9, amount=1000)])
    versions = call(supervisor, 'POST', '/api/rules', rule)['versions']
    rule_id = next(version['id'] for version in versions if version['code'] == rule['code'])
    call(supervisor, 'POST', f'/api/rules/{rule_id}/activation')
    for report in reports:
        call(supervisor, 'POST', f"/api/reports/{report['id']}/points", dict(reference='DGI-RECETTE'))
    credited = call(supervisor, 'GET', f'/api/reports/{rid}/points')
    check('Refus confirmé crédité selon la règle du dépôt malgré une nouvelle activation', credited['net'] == 2)
    call(supervisor, 'POST', f'/api/reports/{rid}/points', dict(reference='DGI-RECETTE'), 409)
    check('Second crédit refusé', True)
    primes = [call(supervisor, 'GET', f"/api/reports/{indexed[f'RECETTE-{person}-00']['id']}/prime")['tiers'][0] for person in range(3)]
    call(supervisor, 'POST', f"/api/primes/{primes[0]['id']}/reviews", dict(result='approved'), 409)
    check('Approbation bloquée avant vérification d’identité', True)
    for person in range(3):
        call(supervisor, 'POST', '/api/beneficiaries', dict(reference=f'RECETTE-{person}-00', beneficiaryReference='PERSONNE-COMMUNE-RECETTE', evidence='JUSTIFICATIF-SYNTHETIQUE'))
    call(supervisor, 'POST', '/api/beneficiaries', dict(reference='RECETTE-0-00', beneficiaryReference='AUTRE-IDENTITE', evidence='TEST'), 409)
    call(supervisor, 'POST', f"/api/primes/{primes[0]['id']}/reviews", dict(result='approved'))
    check('Prime approuvée par un acteur distinct de l’agent', True)

    statement = call(supervisor, 'POST', '/api/payment-statements')
    sid = statement['id']
    call(actors['CELLULE'][0], 'POST', f'/api/payment-statements/{sid}/visas', dict(result='accept'), 409)
    call(supervisor, 'POST', f'/api/payment-statements/{sid}/controls', dict(result='accept'))
    call(supervisor, 'POST', f'/api/payment-statements/{sid}/authorizations', dict(result='accept'), 403)
    call(actors['CELLULE'][0], 'POST', f'/api/payment-statements/{sid}/visas', dict(result='accept'))
    authorized = call(actors['CABINET'][0], 'POST', f'/api/payment-statements/{sid}/authorizations', dict(result='accept'))
    check('Contrôle, visa, autorisation séparés jusqu’à l’état autorisé', authorized['status'] == 'authorized' and authorized['dueOn'] is not None)
    check('Aucun paiement exécuté', sql('select count(*) from app.payment_attempt;', database) == '0')

    call(supervisor, 'POST', '/api/campaign-budget', dict(amount=5000000, reference='BUDGET-RECETTE'))
    call(auditor, 'POST', '/api/campaign-budget', dict(amount=1, reference='INTERDIT'), 403)
    for kind in ['weekly', 'monthly']:
        call(supervisor, 'POST', '/api/campaign-reports', dict(kind=kind))
        call(supervisor, 'POST', '/api/campaign-reports', dict(kind=kind))
    archived = call(auditor, 'GET', '/api/campaign-reports')
    check('Budget historisé et deux rapports archivés sans doublon', len(archived) == 2 and all(row['budget'] == 5000000 for row in archived))

    # Historical approved amount: fixture strictly inside this disposable database.
    sql("insert into app.prime (id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at) values (gen_random_uuid(),'recette-person-0','0990000000','cdc-2026-10-28',9999,980000,'approved','" + actors['SUPERVISOR'][1] + "',now(),now());", database)
    def approve(prime):
        s = login('recette-supervisor', password)
        return s.post(BASE + f"/api/primes/{prime['id']}/reviews", json=dict(result='approved'), headers={'X-XSRF-TOKEN': s.cookies.get('XSRF-TOKEN')}, timeout=30).status_code
    with ThreadPoolExecutor(max_workers=2) as pool:
        statuses = sorted(pool.map(approve, primes[1:]))
    check('Plafond partagé entre trois comptes sous approbations concurrentes', statuses == [200, 409])
    beneficiary = call(supervisor, 'GET', '/api/beneficiaries?reference=RECETTE-0-00')
    check('Plafond de 1 000 000 FC respecté', beneficiary['reservedAmount'] == 1000000 and beneficiary['accounts'] == 3)

    tickets = call(supervisor, 'POST', '/api/draw-tickets', dict(month='2026-11-01'))
    check('Tickets mensuels et finaux uniques', tickets['created'] == 62 and call(supervisor, 'POST', '/api/draw-tickets', dict(month='2026-11-01'))['created'] == 0)
    draw = call(supervisor, 'POST', '/api/draws', dict(kind='monthly', month='2026-11-01', witness='Témoin de recette', campaign=3, intermediate=2, motorcycles=1, cars=0))
    did = draw['draws'][0]['id']
    check('Un seul lot par personne malgré trois comptes', len(draw['prizes']) == 1)
    check('Liste des éligibles figée pour rejouer le tirage', sql('select count(*) from app.draw_entry;', database) == '31')
    published = call(supervisor, 'POST', f'/api/draws/{did}/publications')
    call(supervisor, 'POST', f"/api/draws/prizes/{published['prizes'][0]['id']}/handover", dict(note='Remise synthétique'))
    check('Publication locale et remise du lot enregistrées', call(supervisor, 'GET', '/api/draws')['prizes'][0]['handed'])

    call(supervisor, 'POST', '/api/reexaminations', dict(reference='RECETTE-REFUS', text='Demande synthétique', receivedOn='2026-11-10'))
    reexam = call(supervisor, 'GET', '/api/reexaminations')['items'][0]
    call(supervisor, 'POST', f"/api/reexaminations/{reexam['id']}/decisions", dict(result='maintained', comment='Confirmation maintenue'))
    call(supervisor, 'POST', '/api/reexaminations', dict(reference='RECETTE-REFUS', text='Demande de modification synthétique', receivedOn='2026-11-10'))
    modification = call(supervisor, 'GET', '/api/reexaminations')['items'][0]
    call(supervisor, 'POST', f"/api/reexaminations/{modification['id']}/decisions", dict(result='modified', comment='À corriger'), 409)
    call(supervisor, 'POST', '/api/corrections', dict(reference='RECETTE-REFUS', reason='Infirmation synthétique ultérieure'))
    call(supervisor, 'POST', f"/api/reexaminations/{modification['id']}/decisions", dict(result='modified', comment='Constat infirmé et points annulés'))
    check('Réexamen modifié uniquement après une correction métier réelle', True)
    call(supervisor, 'POST', f'/api/reports/{rid}/points', dict(reference='REJEU'), 409)
    check('Réexamen, correction et impossibilité de recréditer le constat infirmé', call(supervisor, 'GET', f'/api/reports/{rid}/points')['net'] == 0)
    check('Journal d’audit consultable', call(auditor, 'GET', '/api/audit') is not None)
    new_id = str(uuid.uuid4())
    sql("insert into app.report (id, external_id, source, reference_code, reported_on, reported_at, title, description, reporter_user_id, reporter_phone, imported_at) values ('" + new_id + "', 'RECETTE-VERSION', 'recette-version', 'RECETTE-VERSION', timezone('Africa/Kinshasa',clock_timestamp())::date, timezone('Africa/Kinshasa',clock_timestamp()), 'Test de nouvelle règle', 'Données synthétiques', 'recette-version', '0990000009', now());", database)
    call(agent, 'POST', f'/api/reports/{new_id}/qualifications', dict(decision='confirm', finding='non-normalized', proof='photo', justification='Nouvelle version', sellerTaxId='NIF-VERSION'))
    call(supervisor, 'POST', '/api/letter-routes', dict(taxId='NIF-VERSION', service='DGI-A'))
    call(supervisor, 'POST', f'/api/reports/{new_id}/points', dict(reference='DGI-VERSION'))
    version_prime = call(supervisor, 'GET', f'/api/reports/{new_id}/prime')
    check('Nouvelle règle appliquée uniquement au nouveau dépôt', version_prime['netPoints'] == 9 and version_prime['tiers'][0]['amount'] == 1000)
    call(supervisor, 'POST', '/api/beneficiaries', dict(reference='RECETTE-VERSION', beneficiaryReference='PERSONNE-VERSION-RECETTE', evidence='JUSTIFICATIF-SYNTHETIQUE'))
    call(supervisor, 'POST', f"/api/primes/{version_prime['tiers'][0]['id']}/reviews", dict(result='approved'))
    check('Prime d’une nouvelle version approuvable avec ses propres preuves et coordonnées', True)

    sql("insert into app.report (id, external_id, source, reference_code, reported_on, reported_at, title, description, imported_at) select gen_random_uuid(), 'VOLUME-' || n, 'recette-volume', 'VOLUME-' || n, '2026-11-05'::date, '2026-11-05'::timestamp + (n % 86400) * interval '1 second', 'Signalement de volume synthétique', 'Sans image ni OCR', now() from generate_series(1, 100000) n; analyze app.report;", database)
    timings = {}
    for path in ['/api/reports?size=20', '/api/indicators', '/api/seller-files', '/api/abuse-indices']:
        start = time.monotonic()
        call(admin, 'GET', path)
        timings[path] = round(time.monotonic() - start, 3)
    (OUT / 'volume.json').write_text(json.dumps(dict(reports=100032, timings_seconds=timings, limitations='Local synthetic reports without images; not a provider or slow-network load test.'), indent=2))
    check('Consultation des files et indicateurs avec 100 032 signalements isolés', call(admin, 'GET', '/api/reports?size=20')['total'] == 100032)
    runtime = dict(database=database, api=BASE, username='recette-admin', password=password,
        reportId=first['id'], reference='RECETTE-0-00', letterId=letters[0])
    runtime_file = OUT / 'runtime.json'
    runtime_file.touch(mode=0o600, exist_ok=True)
    runtime_file.write_text(json.dumps(runtime))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--keep-running', action='store_true')
    args = parser.parse_args()
    database = 'signalements_recette_' + uuid.uuid4().hex[:12]
    cfg = {k: v for k, v in dotenv_values(ROOT / '.env').items() if v is not None}
    sql('create database "' + database + '";', cfg.get('POSTGRES_DB', 'postgres'))
    source = ThreadingHTTPServer(('127.0.0.1', 0), Source)
    threading.Thread(target=source.serve_forever, daemon=True).start()
    password = 'Recette-' + secrets.token_hex(16)
    env = dict(os.environ, **cfg)
    env.update(SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:' + cfg.get('POSTGRES_PORT', '5432') + '/' + database,
        SERVER_PORT='8081', ACCESS_ADMIN_USERNAME='recette-admin', ACCESS_ADMIN_PASSWORD=password,
        APP_FRONTEND_ORIGIN='http://localhost:3001', SPRING_SESSION_REDIS_NAMESPACE=database,
        FACNO_BASE_URL=f'http://127.0.0.1:{source.server_port}/facno', FACNO_API_KEY='synthetic-key',
        HERDYS_OCR_BASE_URL=f'http://127.0.0.1:{source.server_port}/ocr', HERDYS_OCR_API_TOKEN='synthetic-token',
        REPORTS_SCHEDULE_ENABLED='false', REPORTS_IMPORT_ON_STARTUP='false', LETTERS_AUTOMATION_USERNAME='recette-supervisor', LETTERS_CRON='*/1 * * * * *',
        LOGGING_LEVEL_ROOT='INFO', LOGGING_LEVEL_ORG_SPRINGFRAMEWORK='INFO', LOGGING_LEVEL_ORG_HIBERNATE='WARN')
    log = (OUT / 'backend.log').open('w')
    backend = subprocess.Popen(['java', '-jar', str(ROOT / 'target/signalements-0.0.1-SNAPSHOT.jar')], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        wait_for(lambda: requests.get(BASE + '/actuator/health', timeout=2).status_code == 200)
        run(database, password)
        (OUT / 'result.json').write_text(json.dumps(dict(database=database, checks=CHECKS, passed=len(CHECKS)), ensure_ascii=False, indent=2))
        print(f'RECETTE OK: {len(CHECKS)} contrôles; base isolée {database}', flush=True)
        if args.keep_running:
            print('Serveurs de recette conservés pour les vérifications navigateur.', flush=True)
            threading.Event().wait()
    finally:
        backend.terminate()
        backend.wait(timeout=30)
        source.shutdown()
        log.close()


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""SMS acceptance against a local Unikron stub and isolated PostgreSQL/Redis.
Never loads real Unikron credentials. Requires the private runtime used by recette_validation.py.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import threading
import uuid
import requests

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('validation_recipe', ROOT/'scripts/recette_validation.py')
recipe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recipe)
CALLS = []
PROVIDER_STATUS = 200


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *_): pass
    def do_POST(self):
        assert self.headers.get('Content-Length'), 'SMS must have an explicit content length'
        data = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        assert set(data) == {'username', 'app_key', 'tel', 'message', 'from'}
        assert data['tel'] in {'243827243119','243852214059'}
        assert len(data['message']) <= 160 and len(data['from']) <= 11
        CALLS.append(data)
        self.send_response(PROVIDER_STATUS)
        self.send_header('Content-Length','2')
        self.end_headers()
        self.wfile.write(b'{}')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime',type=Path,required=True)
    parser.add_argument('--keep-running',action='store_true')
    args=parser.parse_args()
    cfg=json.loads(args.runtime.read_text())
    database='sms_acceptance_'+uuid.uuid4().hex[:12]
    def sql(statement,db=None):
        r=subprocess.run(['docker','exec','-i',cfg['postgresContainer'],'psql','-U',cfg['databaseUser'],'-d',db or database,'-v','ON_ERROR_STOP=1','-At'],input=statement,text=True,capture_output=True)
        if r.returncode:raise AssertionError(r.stderr[-500:])
        return r.stdout.strip()
    sql('create database '+database+';',cfg['database'])
    source=ThreadingHTTPServer(('127.0.0.1',0),Provider)
    threading.Thread(target=source.serve_forever,daemon=True).start()
    env=dict(os.environ,SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:65434/'+database,
        SPRING_DATASOURCE_USERNAME=cfg['databaseUser'],SPRING_DATASOURCE_PASSWORD=cfg['databasePassword'],
        SPRING_DATA_REDIS_HOST='127.0.0.1',SPRING_DATA_REDIS_PORT='65435',SPRING_DATA_REDIS_PASSWORD=cfg['redisPassword'],
        SPRING_SESSION_REDIS_NAMESPACE=database,SERVER_PORT='18081',ACCESS_ADMIN_USERNAME='validation-admin',ACCESS_ADMIN_PASSWORD=cfg['adminPassword'],
        REPORTS_SCHEDULE_ENABLED='false',REPORTS_IMPORT_ON_STARTUP='false',REPORT_ANALYSIS_ENABLED='false',
        UNIKRON_ENDPOINT=f'http://127.0.0.1:{source.server_port}/sms',UNIKRON_USER_NAME='fixture-user',UNIKRON_APP_KEY='fixture-key',
        UNIKRON_SENDER_ID='Jabari',UNIKRON_MODE='development')
    with (args.runtime.parent/'sms-backend.log').open('w') as out:
        backend=subprocess.Popen(['java','-jar',str(ROOT/'target/signalements-0.0.1-SNAPSHOT.jar')],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
        try:
            recipe.until(lambda:requests.get(recipe.BASE+'/actuator/health',timeout=2).status_code==200)
            admin=recipe.login('validation-admin',cfg['adminPassword'])
            for role in ['AGENT','SUPERVISOR','AUDITOR']:
                recipe.call(admin,'POST','/api/accounts',dict(username='sms-'+role.lower(),password=cfg['adminPassword'],role=role),201)
            supervisor=recipe.login('sms-supervisor',cfg['adminPassword'])
            agent=recipe.login('sms-agent',cfg['adminPassword'])
            auditor=recipe.login('sms-auditor',cfg['adminPassword'])
            for client in [supervisor,agent,auditor]:recipe.call(client,'POST','/api/sms/tests',expected=403)
            recipe.check('Only administrators can request a test SMS',len(CALLS)==0)
            for phone in ['0827243119','0852214059']:
                result=recipe.call(admin,'POST','/api/sms/tests',dict(phone=phone))
                assert result['status']=='accepted'
            recipe.check('Both authorized numbers are transmitted in international format',set(c['tel'] for c in CALLS)=={'243827243119','243852214059'})
            recipe.call(admin,'POST','/api/sms/tests',dict(phone='0990000000'))
            recipe.check('The test endpoint cannot bypass the development allowlist',len(CALLS)==2)
            ids={}
            for ref,phone in [('SMS-OK','0827243119'),('SMS-BLOCKED','0990000000'),('SMS-UNKNOWN','0852214059'),('SMS-STALE','0827243119')]:
                rid,mid,actor=(str(uuid.uuid4()) for _ in range(3));ids[ref]=mid
                sql(f"insert into app.report (id,external_id,source,reference_code,reported_on,reported_at,title,description,reporter_phone,imported_at) values ('{rid}','{ref}','test','{ref}',current_date,now(),'SMS de recette','Synthétique','{phone}',now());")
                sql(f"insert into app.declarant_message (id,report_id,report_reference,kind,body,status,prepared_by,prepared_at) values ('{mid}','{rid}','{ref}','situation','Message de recette','prepared','{actor}',now());")
            path=f"/api/declarant-messages/{ids['SMS-OK']}/deliveries"
            recipe.call(agent,'POST',path,expected=403);recipe.call(auditor,'POST',path,expected=403)
            recipe.check('Agent and auditor roles cannot send declarant SMS',len(CALLS)==2)
            clients=[recipe.login('validation-admin',cfg['adminPassword']),recipe.login('sms-supervisor',cfg['adminPassword'])]
            def send(client):return client.post(recipe.BASE+path,headers={'X-XSRF-TOKEN':client.cookies.get('XSRF-TOKEN','')},timeout=20).status_code
            with ThreadPoolExecutor(2) as pool:codes=list(pool.map(send,clients))
            recipe.check('Concurrent send requests produce exactly one provider submission',sorted(codes)==[200,409] and len(CALLS)==3)
            messages=recipe.call(admin,'GET','/api/declarant-messages')['items']
            sent=next(m for m in messages if m['id']==ids['SMS-OK'])
            recipe.check('The accepted state and timestamp are persisted without exposing the phone',sent['status']=='accepted' and sent['sentAt'] and 'recipient' not in sent)
            blocked=recipe.call(supervisor,'POST',f"/api/declarant-messages/{ids['SMS-BLOCKED']}/deliveries")
            recipe.check('A prepared message to another number never reaches Unikron',blocked['code']=='recipient-blocked' and len(CALLS)==3)
            global PROVIDER_STATUS
            PROVIDER_STATUS=500
            unknown_path=f"/api/declarant-messages/{ids['SMS-UNKNOWN']}/deliveries"
            result=recipe.call(supervisor,'POST',unknown_path)
            recipe.call(supervisor,'POST',unknown_path,expected=409)
            recipe.check('A provider failure remains uncertain and cannot trigger a duplicate retry',result['status']=='unknown' and len(CALLS)==4)
            sql(f"update app.declarant_message set status='sending',send_attempt_id='{uuid.uuid4()}',send_started_at=now()-interval '1 minute' where id='{ids['SMS-STALE']}';")
            stale=next(m for m in recipe.call(admin,'GET','/api/declarant-messages')['items'] if m['id']==ids['SMS-STALE'])
            recipe.call(admin,'POST',f"/api/declarant-messages/{ids['SMS-STALE']}/deliveries",expected=409)
            recipe.check('An interrupted send is shown as uncertain and is never automatically resent',stale['status']=='unknown' and len(CALLS)==4)
            print(f'SMS ACCEPTANCE OK: {len(recipe.CHECKS)} checks; all provider calls were local stubs.',flush=True)
            (args.runtime.parent/'sms-acceptance-result.json').write_text(json.dumps({'passed':len(recipe.CHECKS),'checks':recipe.CHECKS},indent=2))
            if args.keep_running:threading.Event().wait()
        except KeyboardInterrupt:pass
        finally:
            backend.terminate();backend.wait(timeout=30);source.shutdown()


if __name__=='__main__':main()

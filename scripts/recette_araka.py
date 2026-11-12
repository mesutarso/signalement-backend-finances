#!/usr/bin/env python3
"""Isolated Araka HTTP acceptance. Only local provider stubs; never loads merchant credentials."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
import shutil
from pathlib import Path
import subprocess
import threading
import uuid
from urllib.parse import urlparse
import requests

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('recipe',ROOT/'scripts/recette_validation.py')
recipe=importlib.util.module_from_spec(spec);spec.loader.exec_module(recipe)
CALLS=[];TRANSACTIONS={};MODE='normal'

class Provider(BaseHTTPRequestHandler):
    def log_message(self,*_): pass
    def reply(self,code,body):
        raw=json.dumps(body).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_POST(self):
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))))
        if self.path=='/api/login':
            assert body=={'emailAddress':'fixture-email','password':'fixture-password'}
            return self.reply(200,{'token':'fixture-token'})
        assert self.path=='/api/Pay/sendmobilemoney'
        assert self.headers.get('Authorization')=='Bearer fixture-token'
        assert set(body)=={'order','destination'}
        assert body['order']['currency']=='CDF' and body['order']['amount']==5000
        ref=body['order']['transactionReference'];wallet=body['destination']['walletID']
        assert len(ref)<=20 and ref not in TRANSACTIONS
        assert wallet.startswith('+243') and wallet[-1] in '1234'
        assert body['destination']['provider'] in ['MPESA','AIRTEL','ORANGE','AFRIMONEY']
        CALLS.append(body)
        state='APPROVED' if wallet[-1] in '12' else 'FAILED'
        TRANSACTIONS[ref]={'transactionId':'T-'+ref,'originatingTransactionId':ref,'transactionReference':ref,'status':state,'statusCode':'200' if state=='APPROVED' else '400'}
        if MODE=='uncertain':return self.reply(500,{})
        if MODE=='pending':return self.reply(202,{'statusCode':'202','statusDescription':'ACCEPTED','originatingTransactionId':ref,'transactionId':'T-'+ref})
        return self.reply(200 if state=='APPROVED' else 400,{'statusCode':'200' if state=='APPROVED' else '400','statusDescription':state if state=='APPROVED' else 'Transaction Failed','originatingTransactionId':ref,**({'transactionId':'T-'+ref} if state=='APPROVED' else {})})
    def do_GET(self):
        assert self.path.startswith('/api/Reporting/transactionstatusbyreference/')
        ref=self.path.rsplit('/',1)[-1]
        return self.reply(200,[TRANSACTIONS[ref]] if ref in TRANSACTIONS else [])


def main():
    global MODE
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--keep-running',action='store_true');args=parser.parse_args()
    cfg=json.loads(args.runtime.read_text());database='araka_acceptance_'+uuid.uuid4().hex[:12]
    def sql(statement,db=None):
        r=subprocess.run(['docker','exec','-i',cfg['postgresContainer'],'psql','-U',cfg['databaseUser'],'-d',db or database,'-v','ON_ERROR_STOP=1','-At'],input=statement,text=True,capture_output=True)
        if r.returncode:raise AssertionError(r.stderr[-800:])
        return r.stdout.strip()
    sql('create database '+database+';',cfg['database'])
    source=ThreadingHTTPServer(('127.0.0.1',0),Provider);threading.Thread(target=source.serve_forever,daemon=True).start()
    env=dict(os.environ,SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:65434/'+database,SPRING_DATASOURCE_USERNAME=cfg['databaseUser'],SPRING_DATASOURCE_PASSWORD=cfg['databasePassword'],SPRING_DATA_REDIS_HOST='127.0.0.1',SPRING_DATA_REDIS_PORT='65435',SPRING_DATA_REDIS_PASSWORD=cfg['redisPassword'],SPRING_SESSION_REDIS_NAMESPACE=database,SERVER_PORT='18081',ACCESS_ADMIN_USERNAME='validation-admin',ACCESS_ADMIN_PASSWORD=cfg['adminPassword'],REPORTS_SCHEDULE_ENABLED='false',REPORTS_IMPORT_ON_STARTUP='false',REPORT_ANALYSIS_ENABLED='false',ARAKAPAY_BASE_URL=f'http://127.0.0.1:{source.server_port}',ARAKAPAY_EMAIL='fixture-email',ARAKAPAY_PASSWORD='fixture-password',ARAKAPAY_MODE='sandbox',ARAKAPAY_LIVE_ENABLED='false')
    jar=args.runtime.parent/(database+'.jar')
    shutil.copy2(ROOT/'target/signalements-0.0.1-SNAPSHOT.jar',jar)
    backend=None
    log=(args.runtime.parent/'acceptance-backend.log').open('w')
    def start():
        process=subprocess.Popen(['java','-jar',str(jar)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        recipe.until(lambda:requests.get(recipe.BASE+'/actuator/health',timeout=2).status_code==200)
        return process
    try:
        backend=start();admin=recipe.login('validation-admin',cfg['adminPassword'])
        clients={}
        for role in ['AGENT','SUPERVISOR','AUDITOR','CELLULE','CABINET','PAYMASTER']:
            recipe.call(admin,'POST','/api/accounts',dict(username='araka-'+role.lower(),password=cfg['adminPassword'],role=role),201)
            clients[role]=recipe.login('araka-'+role.lower(),cfg['adminPassword'])
        for role,client in clients.items():recipe.call(client,'POST','/api/payment-transfers/tests',dict(requestId=str(uuid.uuid4()),phone='0810000001',operator='mpesa'),403)
        recipe.call(clients['AGENT'],'GET','/api/payment-transfers',expected=403)
        recipe.check('Only administrators can start sandbox tests; agents cannot read financial transfers',len(CALLS)==0)
        request=str(uuid.uuid4());payload=dict(requestId=request,phone='0810000001',operator='mpesa',amount=1,result='failed',environment='production')
        t=recipe.call(admin,'POST','/api/payment-transfers/tests',payload)
        assert t['state']=='paid' and t['environment']=='sandbox' and float(t['amount'])==5000
        assert recipe.call(admin,'POST','/api/payment-transfers/tests',payload)['id']==t['id']
        recipe.check('Client-supplied results, amounts and environments cannot override the server order',len(CALLS)==1)
        recipe.check('A replayed request ID does not send money again',len(CALLS)==1)
        for operator,prefix in [('mpesa','81'),('orange','84'),('airtel','97'),('afrimoney','90')]:
            for n in range(1,5):
                t=recipe.call(admin,'POST','/api/payment-transfers/tests',dict(requestId=str(uuid.uuid4()),phone=f'+243{prefix}000000{n}',operator=operator))
                assert t['state']==('paid' if n<=2 else 'failed')
        recipe.check('All sixteen documented provider success/failure cases map to final states',len(CALLS)==17)
        for phone,operator in [('0827243119','mpesa'),('0840000001','mpesa'),('0810000005','mpesa')]:
            recipe.call(admin,'POST','/api/payment-transfers/tests',dict(requestId=str(uuid.uuid4()),phone=phone,operator=operator),400)
        recipe.check('Real numbers and mismatched operators are blocked before any provider call',len(CALLS)==17)
        concurrent=dict(requestId=str(uuid.uuid4()),phone='0810000002',operator='mpesa')
        sessions=[recipe.login('validation-admin',cfg['adminPassword']) for _ in range(2)]
        with ThreadPoolExecutor(2) as pool:rows=list(pool.map(lambda client:recipe.call(client,'POST','/api/payment-transfers/tests',concurrent),sessions))
        recipe.check('Concurrent requests with the same key reserve and submit exactly one order',len(CALLS)==18 and rows[0]['id']==rows[1]['id'])
        for role in ['AGENT','SUPERVISOR','AUDITOR','CELLULE','CABINET']:recipe.call(clients[role],'POST',f"/api/payment-transfers/{t['id']}/checks",expected=403)
        MODE='uncertain';payload=dict(requestId=str(uuid.uuid4()),phone='0970000001',operator='airtel')
        uncertain=recipe.call(admin,'POST','/api/payment-transfers/tests',payload)
        assert uncertain['state']=='unknown';recipe.call(admin,'POST','/api/payment-transfers/tests',payload)
        assert len(CALLS)==19
        MODE='normal';settled=recipe.call(admin,'POST',f"/api/payment-transfers/{uncertain['id']}/checks")
        recipe.check('An uncertain submission is recovered by lookup without replaying the payout',settled['state']=='paid' and len(CALLS)==19)
        MODE='pending';pending=recipe.call(admin,'POST','/api/payment-transfers/tests',dict(requestId=str(uuid.uuid4()),phone='0900000001',operator='afrimoney'))
        recipe.check('An accepted request remains pending until business confirmation',pending['state']=='pending')
        MODE='normal'
        report,prime,statement,line,actor=[str(uuid.uuid4()) for _ in range(5)]
        sql(f"insert into app.report(id,external_id,source,reference_code,reported_on,reported_at,title,description,reporter_phone,imported_at,validation_state) values ('{report}','PAY-UI','test','PAY-UI',current_date,now(),'Versement de recette','Synthétique','0810000001',now(),'validated');")
        sql(f"insert into app.prime(id,declarant_id,phone,rule_version,threshold,amount,status,actor_id,recorded_at,updated_at,report_id,reward_setting_id) values ('{prime}','payment-person','0810000001','fixed-reward',0,5000,'approved','{actor}',now(),now(),'{report}','50002026-1112-4000-8000-000000000001');")
        sql(f"insert into app.payout_number values ('{uuid.uuid4()}','payment-person','0810000001','ready','{actor}',now(),now());")
        sql(f"insert into app.payment_statement(id,reference_code,status,prepared_by,prepared_at,controlled_at,visaed_at,authorized_at,authorized_by,due_on) values ('{statement}','EP-RECIPE','authorized','{actor}',now(),now(),now(),now(),'{actor}',current_date);")
        sql(f"insert into app.payment_statement_line values ('{line}','{statement}','{prime}',0,5000,'0810000001',false);")
        for client in [admin,clients['SUPERVISOR'],clients['AUDITOR']]:recipe.call(client,'POST',f'/api/payment-statements/{statement}/transfers',dict(requestId=str(uuid.uuid4()),lineId=line,operator='mpesa'),403)
        payer=clients['PAYMASTER'];count=len(CALLS)
        same=[recipe.login('araka-paymaster',cfg['adminPassword']) for _ in range(2)]
        def send(client):
            r=client.post(recipe.BASE+f'/api/payment-statements/{statement}/transfers',json=dict(requestId=str(uuid.uuid4()),lineId=line,operator='mpesa'),headers={'X-XSRF-TOKEN':client.cookies.get('XSRF-TOKEN','')},timeout=30)
            return r.status_code
        with ThreadPoolExecutor(2) as pool:codes=list(pool.map(send,same))
        recipe.check('Only the paymaster can pay an authorized line and concurrent clicks cannot duplicate it',sorted(codes)==[200,409] and len(CALLS)==count+1)
        recipe.call(payer,'POST',f'/api/payment-statements/{statement}/attempts',dict(lineId=line,operator='m-pesa',result='paid'),410)
        recipe.check('The retired simulation endpoint cannot mark a reward paid',sql('select count(*) from app.payment_attempt;')=='0')
        recipe.check('Confirmed sandbox transfers do not settle real rewards or paid indicators',sql('select count(*) from app.payment_attempt;')=='0' and recipe.call(admin,'GET','/api/indicators')['figures']['payments']==0)
        # Durable recovery: stop the application with an unfinished reservation; only GET may run after restart.
        sql(f"update app.payment_transfer set state='submitting',transaction_id=null,started_at=now()-interval '2 minutes',next_check_at=now()-interval '1 second' where id='{pending['id']}';")
        before=len(CALLS);backend.terminate();backend.wait(timeout=30);backend=start()
        recipe.until(lambda:sql(f"select state from app.payment_transfer where id='{pending['id']}';")=='paid')
        recipe.check('A restart recovers a stale reservation by its original reference without a second submission',len(CALLS)==before)
        cfg.update(recetteDatabase=database,statementId=statement,adminUsername='validation-admin',paymasterUsername='araka-paymaster')
        args.runtime.write_text(json.dumps(cfg))
        result={'passed':len(recipe.CHECKS),'checks':recipe.CHECKS}
        (args.runtime.parent/'acceptance-result.json').write_text(json.dumps(result,indent=2))
        print(f'ARAKA ACCEPTANCE OK: {len(recipe.CHECKS)} checks',flush=True)
        if args.keep_running:threading.Event().wait()
    except KeyboardInterrupt:pass
    finally:
        if backend:backend.terminate();backend.wait(timeout=30)
        source.shutdown();log.close()

if __name__=='__main__':main()

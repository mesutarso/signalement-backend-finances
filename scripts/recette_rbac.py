#!/usr/bin/env python3
"""HTTP acceptance for RBAC and account lifecycle, against an isolated localhost backend."""
import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get('RBAC_BASE_URL', 'http://127.0.0.1:18093').rstrip('/')
if urllib.parse.urlparse(BASE).hostname not in ('localhost', '127.0.0.1'):
    raise SystemExit('La recette RBAC doit utiliser une instance locale isolée.')


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None, expected=200):
        headers = {}
        if method != 'GET':
            self.call('GET', '/api/auth/csrf', expected=204)
            headers['X-XSRF-TOKEN'] = next(c.value for c in self.jar if c.name == 'XSRF-TOKEN')
        payload = None
        if body is not None:
            payload = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(BASE + path, data=payload, method=method, headers=headers)
        try:
            with self.http.open(request, timeout=20) as response:
                status, data = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, data = error.code, error.read()
        assert status == expected, f'{method} {path}: HTTP {status}, attendu {expected}'
        return json.loads(data) if data else None

    def login(self, username, password, expected=200):
        return self.call('POST', '/api/auth', {'username': username, 'password': password}, expected)


def main():
    admin = Client()
    current = admin.login(os.environ['RBAC_ADMIN_USERNAME'], os.environ['RBAC_ADMIN_PASSWORD'])
    catalogue = admin.call('GET', '/api/access/roles')
    assert set(current['permissions']) == {p['code'] for p in catalogue['permissions']}
    assert len(catalogue['roles']) == 9
    admin.call('PATCH', '/api/accounts/' + current['id'], {'enabled': False}, expected=409)
    admin.call('DELETE', '/api/accounts/' + current['id'], expected=409)
    prefix = 'rbac-' + uuid.uuid4().hex[:8]
    password = 'Recette-' + secrets.token_hex(12) + '9'
    users = {}
    checks = 0
    try:
        for role in catalogue['roles']:
            username = prefix + '-' + role['code'].lower()
            created = admin.call('POST', '/api/accounts', {'username': username, 'password': password, 'role': role['code']}, expected=201)
            user = Client()
            principal = user.login(username, password)
            users[role['code']] = (created['id'], username, user)
            assert set(principal['permissions']) == set(role['permissions'])
            for path, permission in [('/api/reports', 'report.read'), ('/api/letters', 'letter.read'), ('/api/payment-statements', 'payment.read'), ('/api/accounts', 'user.read'), ('/api/access/roles', 'rbac.read')]:
                user.call('GET', path, expected=200 if permission in principal['permissions'] else 403)
                checks += 1
            for step, permission in [('controls', 'payment.control'), ('visas', 'payment.visa'), ('authorizations', 'payment.authorize')]:
                user.call('POST', '/api/payment-statements/00000000-0000-4000-8000-000000000000/' + step,
                          {'result': 'accept'}, expected=404 if permission in principal['permissions'] else 403)
                checks += 1
            if role['code'] != 'ADMINISTRATOR':
                user.call('POST', '/api/accounts', {'username': prefix + '-forbidden', 'password': password, 'role': 'ADMINISTRATOR'}, expected=403)
                checks += 1
        target, username, user = users['DGI']
        admin.call('PATCH', '/api/accounts/' + target, {'enabled': False})
        user.call('GET', '/api/auth', expected=401)
        admin.call('PATCH', '/api/accounts/' + target, {'enabled': True})
        user.login(username, password)
        admin.call('PATCH', '/api/accounts/' + target, {'role': 'AUDITOR'})
        user.call('GET', '/api/auth', expected=401)
        target, username, user = users['SUPERVISOR']
        replacement = 'Nouveau-' + secrets.token_hex(12) + '7'
        admin.call('PUT', '/api/accounts/' + target + '/password', {'password': replacement}, expected=204)
        user.call('GET', '/api/auth', expected=401)
        Client().login(username, password, expected=401)
        user = Client()
        user.login(username, replacement)
        for _ in range(5):
            Client().login(username, 'Mauvais-mot-de-passe-99', expected=401)
        Client().login(username, replacement, expected=401)
        admin.call('POST', '/api/accounts/' + target + '/unlock', expected=204)
        user = Client()
        user.login(username, replacement)
        renamed = prefix + '-renomme'
        admin.call('PATCH', '/api/accounts/' + target, {'username': renamed})
        user.call('GET', '/api/auth', expected=401)
        user = Client()
        user.login(renamed, replacement)
        admin.call('DELETE', '/api/accounts/' + target, expected=204)
        user.call('GET', '/api/auth', expected=401)
        assert admin.call('GET', '/api/accounts?search=' + renamed)['total'] == 0
        users.pop('SUPERVISOR')
        checks += 18
        admin.call('GET', '/api/audit')
        print(json.dumps({'roles': len(catalogue['roles']), 'permissions': len(catalogue['permissions']), 'checks': checks, 'result': 'passed'}, ensure_ascii=False))
    finally:
        for account_id, _, _ in users.values():
            admin.call('DELETE', '/api/accounts/' + account_id, expected=204)


if __name__ == '__main__':
    main()

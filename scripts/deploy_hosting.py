#!/usr/bin/env python3
"""Deploys site/ to Firebase Hosting (site "psalmwell") through the Hosting REST API.

Usage: GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> python3 scripts/deploy_hosting.py
The hosting config (cleanUrls, trailingSlash, headers) is read from firebase.json.
"""
import base64, gzip, hashlib, json, os, sys, time, urllib.request, urllib.error
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SA = json.load(open(os.environ['GOOGLE_APPLICATION_CREDENTIALS']))
API = 'https://firebasehosting.googleapis.com/v1beta1'

def b64(b): return base64.urlsafe_b64encode(b).rstrip(b'=')

def token():
    now = int(time.time())
    head = b64(json.dumps({'alg': 'RS256', 'typ': 'JWT'}).encode())
    claim = b64(json.dumps({'iss': SA['client_email'], 'scope': 'https://www.googleapis.com/auth/firebase.hosting https://www.googleapis.com/auth/cloud-platform',
                            'aud': 'https://oauth2.googleapis.com/token', 'iat': now, 'exp': now + 3600}).encode())
    key = serialization.load_pem_private_key(SA['private_key'].encode(), None)
    sig = b64(key.sign(head + b'.' + claim, padding.PKCS1v15(), hashes.SHA256()))
    body = f'grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion={(head + b"." + claim + b"." + sig).decode()}'.encode()
    r = urllib.request.urlopen(urllib.request.Request('https://oauth2.googleapis.com/token', body, {'Content-Type': 'application/x-www-form-urlencoded'}), timeout=30)
    return json.load(r)['access_token']

def call(method, url, tok, data=None, ctype='application/json'):
    if isinstance(data, (dict, list)): data = json.dumps(data).encode()
    req = urllib.request.Request(url, data, {'Authorization': 'Bearer ' + tok, 'Content-Type': ctype}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        sys.exit(f'{method} {url} -> {e.code}: {e.read().decode()[:800]}')

def main():
    cfg = json.load(open(os.path.join(ROOT, 'firebase.json')))['hosting']
    site = cfg['site']; pub = os.path.join(ROOT, cfg['public'])
    tok = token()
    conf = {'cleanUrls': cfg.get('cleanUrls', False)}
    if 'trailingSlash' in cfg:
        conf['trailingSlashBehavior'] = 'ADD' if cfg['trailingSlash'] else 'REMOVE'
    # firebase.json header rules -> REST shape
    conf['headers'] = [{'glob': h['source'], 'headers': {x['key']: x['value'] for x in h['headers']}} for h in cfg.get('headers', [])]
    ver = call('POST', f'{API}/sites/{site}/versions', tok, {'config': conf})
    vname = ver['name']
    files, blobs = {}, {}
    for d, _, fs in os.walk(pub):
        for f in fs:
            p = os.path.join(d, f); rel = '/' + os.path.relpath(p, pub).replace(os.sep, '/')
            gz = gzip.compress(open(p, 'rb').read(), mtime=0); h = hashlib.sha256(gz).hexdigest()
            files[rel] = h; blobs[h] = gz
    pop = call('POST', f'{API}/{vname}:populateFiles', tok, {'files': files})
    for h in pop.get('uploadRequiredHashes', []):
        call('POST', f"{pop['uploadUrl']}/{h}", tok, blobs[h], 'application/octet-stream')
    call('PATCH', f'{API}/{vname}?update_mask=status', tok, {'status': 'FINALIZED'})
    rel = call('POST', f'{API}/sites/{site}/releases?versionName={vname}', tok, {})
    print(json.dumps({'version': vname, 'files': len(files), 'uploaded': len(pop.get('uploadRequiredHashes', [])), 'release': rel.get('name')}))

if __name__ == '__main__':
    main()

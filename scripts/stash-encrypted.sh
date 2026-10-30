#!/usr/bin/env bash
# Encrypts a file with ARTIFACT_KEY and stores it as an unreferenced git blob, so CI outputs can be
# collected by the maintainer without publishing any downloadable build. Prints the blob sha.
set -euo pipefail
in="$1"; label="$2"
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:ARTIFACT_KEY -in "$in" -out "$in.enc"
python3 - "$in.enc" > /tmp/blob.json <<'PY'
import base64, json, sys
print(json.dumps({"content": base64.b64encode(open(sys.argv[1], "rb").read()).decode(), "encoding": "base64"}))
PY
sha=$(curl -sS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/git/blobs" --data-binary @/tmp/blob.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])')
rm -f /tmp/blob.json "$in.enc"
echo "STASH $label $sha $(stat -c %s "$in")"

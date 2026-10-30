#!/usr/bin/env bash
# Encrypts a file with ARTIFACT_KEY and stores it as unreferenced git blobs, so CI outputs can be
# collected by the maintainer without publishing any downloadable build. Large files are stored in
# parts (the blob API refuses very large requests); join the parts in order, then decrypt.
set -euo pipefail
in="$1"; label="$2"
part_size=15000000
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass env:ARTIFACT_KEY -in "$in" -out "$in.enc"
split -b "$part_size" -d -a 2 "$in.enc" "$in.enc.part"
parts=("$in.enc.part"*)
n=${#parts[@]}
i=0
for part in "${parts[@]}"; do
  i=$((i + 1))
  python3 - "$part" > /tmp/blob.json <<'PY'
import base64, json, sys
print(json.dumps({"content": base64.b64encode(open(sys.argv[1], "rb").read()).decode(), "encoding": "base64"}))
PY
  answer=$(curl -sS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$GITHUB_REPOSITORY/git/blobs" --data-binary @/tmp/blob.json)
  sha=$(printf '%s' "$answer" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("sha", ""))' || true)
  if [[ -z "$sha" ]]; then
    echo "::error::blob upload failed for part $i/$n: $(printf '%s' "$answer" | head -c 300)"
    exit 1
  fi
  if (( n == 1 )); then
    echo "STASH $label $sha $(stat -c %s "$in")"
  else
    echo "STASH $label part $i/$n $sha $(stat -c %s "$part")"
  fi
done
(( n == 1 )) || echo "STASH $label total $(stat -c %s "$in") bytes in $n parts"
rm -f /tmp/blob.json "$in.enc" "$in.enc.part"*

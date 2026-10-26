#!/usr/bin/env bash
# Fails if a user-visible string is written as a literal in the app UI; text belongs in res/values/strings.xml.
set -uo pipefail
cd "$(dirname "$0")/.."
hits=$(grep -rnE 'Text\("|contentDescription = "|setContentTitle\("|setContentText\("|title = \{ Text\("' --include='*.kt' --exclude-dir=build app/src || true)
if [ -n "$hits" ]; then
  echo "hard-coded UI strings (move them to strings.xml):"
  echo "$hits"
  exit 1
fi
echo "no hard-coded UI strings in app/src"

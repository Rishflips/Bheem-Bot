#!/usr/bin/env bash
# Fails if a user-visible string is written as a literal in the app UI or its notifications; text belongs in res/values/strings.xml.
# It is a grep, so it cannot see every shape of literal (text joined in code, for example); the review still looks for those.
set -uo pipefail
cd "$(dirname "$0")/.."
pattern='Text\("|contentDescription *= *"|setContentTitle\("|setContentText\("|NotificationChannel\([^)]*, *"|[^a-zA-Z]text = "[^"]|placeholder = \{ *Text\("|label = \{ *Text\("'
hits=$(grep -rnE "$pattern" --include='*.kt' --exclude-dir=build app/src || true)
if [ -n "$hits" ]; then
  echo "hard-coded UI strings (move them to strings.xml):"
  echo "$hits"
  exit 1
fi
echo "no hard-coded UI strings in app/src"

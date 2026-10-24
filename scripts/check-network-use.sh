#!/usr/bin/env bash
# Fails if any code outside the downloader module talks to the network.
set -uo pipefail
cd "$(dirname "$0")/.."
pattern='java\.net\.|okhttp|HttpURLConnection|URLConnection|android\.net\.http|javax\.net|java\.nio\.channels|DownloadManager|android\.webkit|WebView'
found=$(mktemp)
grep -rnE "$pattern" --include='*.kt' --include='*.java' --include='*.kts' --exclude-dir=build --exclude-dir=.gradle --exclude-dir=downloader . > "$found"
status=$?
if [ "$status" -gt 1 ]; then
  echo "grep failed with status $status"
  exit 2
fi
hits=$(grep -v '^\./downloader/' "$found" || true)
rm -f "$found"
if [ -n "$hits" ]; then
  echo "network use outside the downloader module:"
  echo "$hits"
  exit 1
fi
echo "no network use outside the downloader module"

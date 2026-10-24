#!/usr/bin/env bash
# Fails if any code outside the downloader module talks to the network.
set -u
cd "$(dirname "$0")/.."
pattern='java\.net\.|okhttp|HttpURLConnection|URLConnection|android\.net\.http|javax\.net|java\.nio\.channels\.SocketChannel'
hits=$(grep -rnE "$pattern" --include='*.kt' --include='*.java' --include='*.kts' . \
  | grep -v '^\./downloader/' \
  | grep -v '/build/' || true)
if [ -n "$hits" ]; then
  echo "network use outside the downloader module:"
  echo "$hits"
  exit 1
fi
echo "no network use outside the downloader module"

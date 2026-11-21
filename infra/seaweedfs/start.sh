#!/bin/sh
# Starts SeaweedFS for private CV storage.
#
# Only the credentialed S3 gateway (port 8333) listens on the container network. The master,
# volume and filer servers have no authentication of their own, so they bind to loopback inside
# the container and are reachable only by the gateway. Anonymous S3 requests are denied.
# Credentials come from the environment; the generated config never leaves the container.
set -eu

: "${STORAGE_ACCESS_KEY:?STORAGE_ACCESS_KEY is required}"
: "${STORAGE_SECRET_KEY:?STORAGE_SECRET_KEY is required}"
: "${STORAGE_BUCKET:?STORAGE_BUCKET is required}"

umask 077
printf '{"identities":[{"name":"backend","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Read","Write","List","Admin"]}]}' \
  "$STORAGE_ACCESS_KEY" "$STORAGE_SECRET_KEY" > /tmp/s3.json

# Storage servers: loopback only, no built-in S3/WebDAV/admin UI.
weed mini -dir=/data -ip=127.0.0.1 -ip.bind=127.0.0.1 -s3=false -webdav=false -admin.ui=false &
MINI=$!
trap 'kill "$MINI" 2>/dev/null' TERM INT

# Wait for the filer before starting the gateway.
i=0
until wget -q -O /dev/null http://127.0.0.1:8888/ 2>/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 120 ] || ! kill -0 "$MINI" 2>/dev/null; then
    echo "SeaweedFS storage servers did not start" >&2
    exit 1
  fi
  sleep 0.5
done

# The only network-facing service: S3 with credentials.
weed s3 -ip.bind=0.0.0.0 -port=8333 -filer=127.0.0.1:8888 -config=/tmp/s3.json &
S3=$!
trap 'kill "$S3" "$MINI" 2>/dev/null' TERM INT

# Exit (and let Docker restart the container) if either process stops.
while kill -0 "$MINI" 2>/dev/null && kill -0 "$S3" 2>/dev/null; do
  sleep 2
done
kill "$S3" "$MINI" 2>/dev/null || true
exit 1

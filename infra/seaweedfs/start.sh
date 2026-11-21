#!/bin/sh
# Starts SeaweedFS in single-node S3 mode with one credentialed identity (anonymous access denied).
# Credentials come from the environment; the generated config never leaves the container.
set -eu

: "${STORAGE_ACCESS_KEY:?STORAGE_ACCESS_KEY is required}"
: "${STORAGE_SECRET_KEY:?STORAGE_SECRET_KEY is required}"
: "${STORAGE_BUCKET:?STORAGE_BUCKET is required}"

umask 077
printf '{"identities":[{"name":"backend","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Read","Write","List","Admin"]}]}' \
  "$STORAGE_ACCESS_KEY" "$STORAGE_SECRET_KEY" > /tmp/s3.json

exec weed mini -dir=/data -bucket="$STORAGE_BUCKET" -s3.config=/tmp/s3.json -ip.bind=0.0.0.0

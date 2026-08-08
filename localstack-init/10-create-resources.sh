#!/usr/bin/env bash
set -euo pipefail

bucket="${APP_S3_BUCKET:-grain-files}"
key_alias="${APP_KMS_KEY_ID:-alias/grain-local-key}"

if ! awslocal s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
  awslocal s3api create-bucket --bucket "$bucket" >/dev/null
fi

if ! awslocal kms describe-key --key-id "$key_alias" >/dev/null 2>&1; then
  key_id="$(awslocal kms create-key --description "Grain local development key" \
    --query KeyMetadata.KeyId --output text)"
  awslocal kms create-alias --alias-name "$key_alias" --target-key-id "$key_id"
fi

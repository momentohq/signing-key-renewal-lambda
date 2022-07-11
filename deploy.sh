#!/usr/bin/env bash
set -ex

./gradlew build
pushd infrastructure/
  npm install
  npm run build
  npx cdk deploy momento-signing-key-renewal-stack --require-approval never --parameters momentoAuthTokenSecretArn="$MOMENTO_AUTH_TOKEN_SECRET_ARN"
popd

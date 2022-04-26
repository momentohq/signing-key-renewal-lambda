#!/usr/bin/env bash
set -ex

mvn clean install
pushd infrastructure/
  npm run build
  npx cdk deploy momento-signing-key-renewal-stack --require-approval never
popd

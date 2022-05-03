#!/usr/bin/env bash
set -ex

pushd infrastructure/
  npx cdk destroy momento-signing-key-renewal-stack
popd

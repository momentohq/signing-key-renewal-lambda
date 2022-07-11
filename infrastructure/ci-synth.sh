#!/usr/bin/env bash

# This is a bash script file Momento uses to verify that the CloudFormation stack actually synthesizes
set -e
set -x
set -o pipefail

npm ci
npm run build
npx cdk synth -v

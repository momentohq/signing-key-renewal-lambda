#!/usr/bin/env bash
set -e

private_repo=$(cat infrastructure/package-lock.json | grep "momento-prod")
if [ -z "$private_repo" ]
then
  echo "package-lock.json is ok"
else
  echo "ERROR: package-lock.json contains private NPM repos, this must be fixed"
  exit 1
fi

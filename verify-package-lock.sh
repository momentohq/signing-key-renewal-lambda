#!/usr/bin/env bash
set -ex

# Don't let grep exit if not found, that's intended.
# This script is ran within our GitHub workflows at the root dir of the project
private_repo=$(cat infrastructure/package-lock.json | { grep "momento-prod" || true; })
if [ -z "$private_repo" ]
then
  echo "package-lock.json is ok"
else
  echo "ERROR: package-lock.json contains private NPM repos, this must be fixed"
  exit 1
fi

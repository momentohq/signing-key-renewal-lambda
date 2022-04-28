# Momento Signing Key Renewal Lambda
This repo provides an example solution to auto-renew your Momento signing keys. This is done via a Java8 function deployed to your AWS account.

## What does this do?
This Lambda automates the process for renewing your Momento signing key. If you haven't created a Secret within Secrets Manager yet for a key, that's okay! The included lambda can create it for you if need be.



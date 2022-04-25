# Momento Signing Key Renewal Lambda
This repo provides an example solution to auto-renew your Momento signing keys. This is done via a Java8 function deployed to your AWS account.

## How to build and deploy
1. Run `mvn clean install`
2. Modify the environment variables [passed in to the lambda](./infrastructure/lib/infrastructure-stack.ts). Modify the following:
   1. `TBD LIST OF ENVIRONMENT VARIABLES`
3. `cd` to `infrastructure` and run:
   1. `npm install`
   2. `npm run build`
   3. `AWS_PROFILE=<YOUR_AWS_PROFILE_NAME_HERE> npx cdk deploy momento-signing-key-renewal-stack --require-approval never`


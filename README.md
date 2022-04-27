# Momento Signing Key Renewal Lambda
This repo provides an example solution to auto-renew your Momento signing keys. This is done via a Java8 function deployed to your AWS account.

## What does this do?
This Lambda automates the process for renewing your Momento signing key. If you haven't created a Secret within Secrets Manager yet for a key, that's okay! The included lambda can create it for you if need be.

## How to build and deploy
* To build the Java files, run `mvn clean install`
* To build the CDK output, run `cd infrastructure && npm run build`
* To deploy to your own account, run:
```shell
export AWS_PROFILE=<YOUR_AWS_PROFILE_NAME>
   # export SIGNING_KEY_TTL_MINUTES=<TTL_IN_MINUTES_FOR_SIGNING_KEY> \ # Optional
   # export RENEW_WITHIN_DAYS=<TIME_TO_RENEW_IN_DAYS> \ # Optional
   # export EXPORT_METRICS=true \ # Optional
   # export KMS_KEY_ARN=<YOUR_KMS_KEY_ARN> \ # Optional
   export MOMENTO_AUTH_SECRET_ID=<YOUR_SECRET_IN_SECRETS_MANAGER_WITH_YOUR_MOMENTO_AUTH_TOKEN>
   export MOMENTO_AUTH_SECRET_KEY_NAME=<THE_KEY_VALUE_OF_YOUR_MOMENTO_AUTH_SECRET>
   export MOMENTO_SIGNING_KEY_SECRET_ID=<SECRET_KEY_ID_FOR_YOUR_MOMENTO_SIGNING_KEY>
   ./deploy.sh
```

## Example deploy command
Here is an example assuming the following:
* You have an `AWS_PROFILE` set up with the name `personal`
* You have already stored your Momento auth token safely inside Secrets Manager named `"my/momento/auth-token"` and serialized it as the following:
```json
{ "secret": "<momento_auth_token>" }
```
* You have/desire a secret in Secrets Manager named `"my/momento/signing-key"` for your signing key serialized as:
```json
{ "signingKey": "{<serialized JSON string from CreateSigningKey response>}" }
```

You would run the following command to deploy:

```shell
export AWS_PROFILE=personal
  export MOMENTO_AUTH_SECRET_ID="my/momento/auth-token"
  export MOMENTO_AUTH_SECRET_KEY_NAME="secret"
  export MOMENTO_SIGNING_KEY_SECRET_ID="my/momento/signing-key"
  ./deploy.sh
```

## To tear down stack
```shell
AWS_PROFILE=<YOUR_AWS_PROFILE_NAME> ./teardown.sh
```

## Local testing
You can use Docker to build and test changes as desired. First, create a `.env` file with the following:

```shell
AWS_REGION=<region>
SECRETS_MANAGER_REGION=<region>
AWS_ACCESS_KEY_ID=<access_key>
AWS_SECRET_ACCESS_KEY=<secret_access_key>
AWS_SESSION_TOKEN=<session_token>
SIGNING_KEY_TTL_MINUTES=<ttl>
RENEW_WITHIN_DAYS=<time>
MOMENTO_AUTH_TOKEN_SECRET_ID=<secret_id>
MOMENTO_AUTH_TOKEN_SECRET_KEY_NAME=<key_name>
MOMENTO_SIGNING_KEY_SECRET_ID=<secret_id>
```

Ensure none of these have quotes around them.

Compile with:

```shell
mvn compile dependency:copy-dependencies -DincludeScope=runtime
```

Build the docker image:

```shell
docker build -t signing-key-renewal .
```

And then run with:

```shell
docker run --env-file=.env -p 9000:8080 signing-key-renewal
```

In a separate terminal:

```shell
curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" -d '{}'
```

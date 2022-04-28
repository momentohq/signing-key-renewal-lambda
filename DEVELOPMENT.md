# Local development

## Prerequisites
* You have an AWS account
* You have generated a Momento auth token and stored it as plaintext in Secrets Manager
* You have created a secret in Secrets Manger containing either blank output _or_ the full serialized JSON from a Momento `CreateSigningKey` response

## How to build and deploy
* To build the Java files, run `mvn clean install`
* To build the CDK output, run `cd infrastructure && npm run build`
* To deploy to your own account, run:
```shell
export AWS_PROFILE=<YOUR_AWS_PROFILE_NAME>
   # export SIGNING_KEY_TTL_MINUTES=<TTL_IN_MINUTES_FOR_SIGNING_KEY> \ # Optional
   # export EXPORT_METRICS=true \ # Optional
   # export KMS_KEY_ARN=<YOUR_KMS_KEY_ARN> \ # Optional
   export MOMENTO_AUTH_SECRET_ID=<YOUR_SECRET_IN_SECRETS_MANAGER_WITH_YOUR_MOMENTO_AUTH_TOKEN>
   ./deploy.sh
```

From here, you can manually add your own rotation schedule to your secret.

## Example deploy command
Here is an example assuming the following:
* You have an `AWS_PROFILE` set up with the name `personal`
* You have already stored your Momento auth token safely inside Secrets Manager named `"my/momento/auth-token"`
* You have/desire a secret in Secrets Manager named `"my/momento/signing-key"` for your signing key

You would run the following command to deploy:

```shell
export AWS_PROFILE=personal \
  export MOMENTO_AUTH_SECRET_ID="my/momento/auth-token"
  ./deploy.sh
```

## To tear down stack
```shell
AWS_PROFILE=<YOUR_AWS_PROFILE_NAME> ./teardown.sh
```

or delete the `momento-signing-key-renewal-stack` resource from CloudFormation manually.

## Local testing without deploying
You can use Docker to build and test changes as desired. You will need to create a `.env` file with the necessary
environment variables.

***If you want to test real events, fill your .env file with the following***:

```shell
AWS_REGION=<region>
SECRETS_MANAGER_REGION=<region>
AWS_ACCESS_KEY_ID=<access_key>
AWS_SECRET_ACCESS_KEY=<secret_access_key>
AWS_SESSION_TOKEN=<session_token>
SIGNING_KEY_TTL_MINUTES=<ttl>
MOMENTO_AUTH_TOKEN_SECRET_ID=<secret_id>
EXPORT_METRICS=<true|false>
```

***If you want to test local changes without making AWS calls (but still making Momento calls)***:
```shell
AWS_REGION=<region>
SECRETS_MANAGER_REGION=<region>
SIGNING_KEY_TTL_MINUTES=<ttl>
MOMENTO_AUTH_TOKEN_SECRET_ID=<secret_id>
EXPORT_METRICS=<true|false>
USE_LOCAL_STUBS=true
TEST_AUTH_TOKEN=<your actual momento auth token>
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
curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" -d @events/createSecretEvent.json # or whichever event you'd like
```

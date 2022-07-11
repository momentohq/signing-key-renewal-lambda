# DEVELOPMENT 

## Prerequisites
* Java 8
* Node 16+
* Gradle
* You have your Momento auth token stored away in Secrets Manager (see [README](./README.md) for instructions)

## How to update Momento SDK
Edit [build.gradle.kts](./build.gradle.kts) and edit this line:
```kotlin
implementation("momento.sandbox:momento-sdk:0.21.0")
```


## How to build and deploy
* To build the Java files, run `./gradlew build`
* To build the CDK output, run `cd infrastructure && npm install && npm run build`
* To deploy to your own account, run:
```shell
AWS_PROFILE=<YOUR_AWS_PROFILE_NAME> \
MOMENTO_AUTH_TOKEN_SECRET_ARN=<ARN_OF_MOMENTO_AUTH_TOKEN_SECRET> ./deploy.sh
```

Below is a list of optional environment variables you can pass in:
* `MOMENTO_SIGNING_KEY_SECRET_NAME`: override the name of the Secret created by the stack to store your signing key. **Default:** `momento/signing-key`
* `SIGNING_KEY_TTL_MINUTES`: override the ttl of the signing key (in minutes) when the key is renewed. **Default:** 14 days
* `AUTO_ROTATION_IN_DAYS`: override the schedule (in days) in which the signing key will be renewed. **Default:** 11 days
* `EXPORT_METRICS`: set to `true` if you would like the lambda to publish CloudWatch metrics to your account indicating the time until the signing key expires. **Default:** `false`
* `KMS_KEY_ARN`: override if you want to use your own KMS key to encrypt your secret in Secrets Manager. **Default:** `null`

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
AWS_ACCESS_KEY_ID=<access_key>
AWS_SECRET_ACCESS_KEY=<secret_access_key>
AWS_SESSION_TOKEN=<session_token>
SIGNING_KEY_TTL_MINUTES=<ttl>
MOMENTO_AUTH_TOKEN_SECRET_ARN=<secret_arn>
EXPORT_METRICS=<true|false>
```

***If you want to test local changes without making AWS calls (but still making Momento calls)***:
```shell
AWS_REGION=<region>
SIGNING_KEY_TTL_MINUTES=<ttl>
MOMENTO_AUTH_TOKEN_SECRET_ARN=<secret_id>
EXPORT_METRICS=<true|false>
USE_LOCAL_STUBS=true
TEST_AUTH_TOKEN=<your actual momento auth token>
```

Ensure none of these have quotes around them.

Compile with:

```shell
./gradlew build
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

You can also test manual rotation:
```shell
curl -XPOST "http://localhost:9000/2015-03-31/functions/function/invocations" -d '{"momento_auth_token":"<key>", "momento_signing_key_secret_name": "momento/signing-key", "signing_key_ttl_minutes": 60}'
```

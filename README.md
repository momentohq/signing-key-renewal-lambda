# Momento Signing Key Renewal Lambda
This repo provides an example solution to manage and auto-renew your Momento signing keys. This is done via a Java 8 function deployed to your AWS account.

## Prerequisites
1. You have an AWS account
2. You have a Momento auth token, and you've stored it (in plaintext) in Secrets Manager
   1. If you haven't done so yet, you can create one with the following command:
```shell
aws secretsmanager create-secret --name "momento/auth-token" --secret-string "<YOUR MOMENTO AUTH TOKEN>"
```
Ensure you have the ARN of your secret containing your Momento auth token available.

## Deploying to account
Simply click this button below to one-click deploy the CloudFormation stack to your account.

**BUTTON UNDER CONSTRUCTION**

Alternatively, if you would like to make local changes and deploy via CLI, follow the instructions here:

[DEVELOPMENT](./DEVELOPMENT.md)

### Manual rotation
With this lambda deployed you can also manually invoke your lambda to rotate a secret for you. Simply send an event with the following properties:
```json
{
  "momento_auth_token":"<your auth token here>",
  "momento_signing_key_secret_name":"<name of secret to rotate>",
  "signing_key_ttl_minutes": "<ttl for signing key in minutes>",
  "export_metrics": false,
  "use_local_stubs": false
}
```

## Retrieving signing key from secret
Your application simply needs to retrieve the newly-generated Secret from Secrets Manager. The secret name (unless overwritten) is `momento/signing-key`.
The returned value will look similar to this:
```json
{
   "key_id": "<some_id>",
   "key": "<JSON string>",
   "expires_at": "2022-04-28T23:09:52Z",
   "endpoint": "<endpoint URL>"
}
```

Example using the AWS CLI and `jq`:

```shell
aws secretsmanager get-secret-value --secret-id "momento/signing-key" | jq '.SecretString | fromjson'
{
  "key_id": "<id>",
  "key": "<serialized JSON string>"
  "expires_at": "2022-04-28T23:09:52Z",
  "endpoint": "<endpoint>"
}
```

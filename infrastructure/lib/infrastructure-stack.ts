import * as cdk from 'aws-cdk-lib';
import {Duration, Fn} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as events from 'aws-cdk-lib/aws-events';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect, ManagedPolicy, PolicyStatement, ServicePrincipal} from 'aws-cdk-lib/aws-iam';

interface Props extends cdk.StackProps {
  // The Secret Id containing your Momento auth token (best practice)
  momentoAuthTokenSecretId: string;
  // The 'key' value of your JSON containing your Momento auth token.
  // Example: { "myKey" : "<token>" } <-- "myKey" would be the 'key' value
  momentoAuthTokenSecretKeyName: string;
  // The Secret Id containing your Momento signing key information.
  // If this does not exist, the renewal lambda will create it for you
  momentoSigningKeySecretId: string;
  // Set to true if you would like metrics exported to your AWS account
  exportMetrics: boolean;
  // Override this value if your secrets reside in a different AWS region
  secretsManagerRegion?: string;
  // The desired TTL in minutes until a newly-renewed signing key expires
  signingKeyTtlMinutes: number;
  // Specifies how soon to renew a signing key.
  // Example: if `renewWithinDays` is set to 3, the renewal lambda will check the `expiresAt` field of the
  // signing key. If the key will expire within 3 days, the lambda will renew the key.
  renewWithinDays: number;
}

export class InfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Props) {
    super(scope, id);

    const lambdaRole = new iam.Role(this, `momento-signing-key-renewal-lambda-role`, {
      assumedBy: new ServicePrincipal('lambda.amazonaws.com'),
      managedPolicies: [
        ManagedPolicy.fromManagedPolicyArn(this, `momento-signing-key-renewal-policy`, 'arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole'),
      ]
    });
    lambdaRole.addToPolicy(new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["secretsmanager:GetSecretValue", "secretsmanager:CreateSecret", "secretsmanager:UpdateSecret"],
      // For best-practices, restrict permissions to the ARN of your secret key
      resources: ['*']
    }));

    const func = new lambda.Function(this, `momento-signing-key-renewal-lambda`, {
      runtime: lambda.Runtime.JAVA_8_CORRETTO,
      code: lambda.Code.fromAsset('../target/signing-key-renewal-lambda-1.0-SNAPSHOT.jar'),
      handler: 'example.Handler',
      functionName: `momento-signing-key-renewal-lambda`,
      timeout: Duration.seconds(35),
      memorySize: 512,
      role: lambdaRole,
      environment: {
        MOMENTO_AUTH_TOKEN_SECRET_ID: props.momentoAuthTokenSecretId,
        MOMENTO_AUTH_TOKEN_SECRET_KEY_NAME: props.momentoAuthTokenSecretKeyName,
        MOMENTO_SIGNING_KEY_SECRET_ID: props.momentoSigningKeySecretId,
        EXPORT_METRICS: props.exportMetrics.toString(),
        SECRETS_MANAGER_REGION: props.secretsManagerRegion ?? Fn.sub("${AWS::Region}"),
        SIGNING_KEY_TTL_MINUTES: props.signingKeyTtlMinutes.toString(),
        RENEW_WITHIN_DAYS: props.renewWithinDays.toString(),
      }
    })

    const target = new targets.LambdaFunction(func, {});
    const event = new events.Rule(this, `momento-signing-key-renewal-event-rule`, {
      schedule: events.Schedule.rate(Duration.days(1)),
      targets: [target]
    })
  }
}

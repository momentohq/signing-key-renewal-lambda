import * as cdk from 'aws-cdk-lib';
import {Duration, Fn} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect, ManagedPolicy, PolicyStatement, ServicePrincipal} from 'aws-cdk-lib/aws-iam';

interface Props extends cdk.StackProps {
  // The Secret Id containing your Momento auth token as plaintext (best practice)
  momentoAuthTokenSecretId: string;
  // Set to true if you would like metrics exported to your AWS account
  exportMetrics: boolean;
  // The desired TTL in minutes until a newly-renewed signing key expires
  signingKeyTtlMinutes: number;
  // Override this value if your secrets reside in a different AWS region
  secretsManagerRegion?: string;
  // Set this if you are not using the default AWS KMS key for your secret
  kmsKeyArn?: string;
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
      actions: [
        "secretsmanager:GetSecretValue",
        "secretsmanager:CreateSecret",
        "secretsmanager:PutSecretValue",
        "secretsmanager:DescribeSecret",
        "secretsmanager:UpdateSecretVersionStage",
      ],
      // For best-practices, restrict permissions to the ARN of your secret key AND the secret containing your
      // Momento auth token
      resources: ['*']
    }));

    if (props.kmsKeyArn !== undefined) {
      lambdaRole.addToPolicy(new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ["kms:Decrypt",  "kms:GenerateDataKey"],
        resources: [props.kmsKeyArn]
      }));
    }

    if (props.exportMetrics) {
      lambdaRole.addToPolicy(new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ["cloudwatch:PutMetricData"],
        resources: ["*"]
      }));
    }

    const func = new lambda.Function(this, `momento-signing-key-renewal-lambda`, {
      runtime: lambda.Runtime.JAVA_8_CORRETTO,
      code: lambda.Code.fromAsset('../target/signing-key-renewal-lambda-1.0-SNAPSHOT.jar'),
      handler: 'example.Handler',
      functionName: `momento-signing-key-renewal-lambda`,
      timeout: Duration.seconds(60),
      memorySize: 512,
      role: lambdaRole,
      environment: {
        MOMENTO_AUTH_TOKEN_SECRET_ID: props.momentoAuthTokenSecretId,
        EXPORT_METRICS: props.exportMetrics.toString(),
        SECRETS_MANAGER_REGION: props.secretsManagerRegion ?? Fn.sub("${AWS::Region}"),
        SIGNING_KEY_TTL_MINUTES: props.signingKeyTtlMinutes.toString(),
      }
    });

    func.grantInvoke(new iam.ServicePrincipal("secretsmanager.amazonaws.com"));
  }
}

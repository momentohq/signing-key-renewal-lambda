import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as kms from 'aws-cdk-lib/aws-kms';
import {
  Effect,
  ManagedPolicy,
  PolicyStatement,
  ServicePrincipal,
} from 'aws-cdk-lib/aws-iam';

interface Props extends cdk.StackProps {
  // The name you would like to give to the Secret containing your Momento signing key
  momentoSigningKeySecretName?: string;
  // Set to true if you would like metrics exported to your AWS account
  exportMetrics: boolean;
  // The desired TTL in minutes until a newly-renewed signing key expires
  signingKeyTtlMinutes: number;
  // Override this if you wish to change when the secret is automatically rotated.
  // IMPORTANT: This MUST be less than signingKeyTtlMinutes
  rotateAutomaticallyAfterInDays: number;
  // Override this if you are not using the default AWS KMS key for your secret
  kmsKeyArn?: string;
}

export class InfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: Props) {
    super(scope, id);

    // Having a CfnParameter here should ensure that one-click deploy users can pass in their Secret ARN containing
    // their Momento auth token
    const momentoAuthTokenSecretArnParam = new cdk.CfnParameter(this, "momentoAuthTokenSecretArn", {
      type: "String",
      description: "The ARN containing your Momento auth token as plaintext (best practice)"
    });

    InfrastructureStack.validateRotationParams(
      props.signingKeyTtlMinutes,
      props.rotateAutomaticallyAfterInDays
    );

    const signingKeyName = props.momentoSigningKeySecretName
      ? props.momentoSigningKeySecretName
      : 'momento/signing-key';
    let momentoSigningKeySecret: secretsmanager.Secret;

    const lambdaRole = new iam.Role(
      this,
      'momento-signing-key-renewal-lambda-role',
      {
        assumedBy: new ServicePrincipal('lambda.amazonaws.com'),
        managedPolicies: [
          ManagedPolicy.fromManagedPolicyArn(
            this,
            'momento-signing-key-renewal-policy',
            'arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole'
          ),
        ],
      }
    );
    if (props.kmsKeyArn !== undefined) {
      lambdaRole.addToPolicy(
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ['kms:Decrypt', 'kms:GenerateDataKey'],
          resources: [props.kmsKeyArn],
        })
      );
      momentoSigningKeySecret = new secretsmanager.Secret(
        this,
        'momento-signing-key-secret',
        {
          secretName: signingKeyName,
          encryptionKey: kms.Key.fromKeyArn(
            this,
            'secret-kms-key',
            props.kmsKeyArn
          ),
        }
      );
    } else {
      momentoSigningKeySecret = new secretsmanager.Secret(
        this,
        'momento-signing-key-secret',
        {
          secretName: signingKeyName,
        }
      );
    }
    lambdaRole.addToPolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: [
          'secretsmanager:GetSecretValue',
          'secretsmanager:CreateSecret',
          'secretsmanager:PutSecretValue',
          'secretsmanager:DescribeSecret',
          'secretsmanager:UpdateSecretVersionStage',
        ],
        resources: ["*"],
      })
    );
    lambdaRole.addToPolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['secretsmanager:GetSecretValue'],
        resources: ["*"],
      })
    );

    lambdaRole.addToPolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      })
    );

    const func = new lambda.Function(
      this,
      'momento-signing-key-renewal-lambda',
      {
        runtime: lambda.Runtime.JAVA_8_CORRETTO,
        code: lambda.Code.fromAsset(
          '../build/libs/signing-key-renewal-lambda-1.0-SNAPSHOT.jar'
        ),
        handler: 'example.Handler',
        functionName: 'momento-signing-key-renewal-lambda',
        timeout: Duration.seconds(60),
        memorySize: 512,
        role: lambdaRole,
        environment: {
          MOMENTO_AUTH_TOKEN_SECRET_ARN: momentoAuthTokenSecretArnParam.valueAsString,
          EXPORT_METRICS: props.exportMetrics.toString(),
          SIGNING_KEY_TTL_MINUTES: props.signingKeyTtlMinutes.toString(),
        },
      }
    );

    func.grantInvoke(new iam.ServicePrincipal('secretsmanager.amazonaws.com'));

    momentoSigningKeySecret.addRotationSchedule('rotation-schedule', {
      rotationLambda: func,
      automaticallyAfter: Duration.days(props.rotateAutomaticallyAfterInDays),
    });
  }

  private static validateRotationParams(
    signingKeyTtlMinutes: number,
    rotateAutomaticallyAfterInDays: number
  ) {
    const rotateAutomaticallyAfterInMinutes = Duration.days(
      rotateAutomaticallyAfterInDays
    ).toMinutes();
    if (!(rotateAutomaticallyAfterInMinutes < signingKeyTtlMinutes)) {
      throw new Error(
        'rotateAutomaticallyAfterInDays must be less than signingKeyTtlMinutes'
      );
    }
  }
}

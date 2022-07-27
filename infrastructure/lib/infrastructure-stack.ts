import * as cdk from 'aws-cdk-lib';
import {Duration} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as kms from 'aws-cdk-lib/aws-kms';
import * as path from 'path';
import {
  Effect,
  ManagedPolicy,
  PolicyStatement,
  ServicePrincipal,
} from 'aws-cdk-lib/aws-iam';

interface SigningKeyOptions {
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
  // Override this if you are not storing your Momento auth token as a simple string.
  // Example: if this it is persisted in SecretsManager as '{"token": "<momento auth token value>"}', you
  // would pass in "token"
  authTokenKeyValue?: string;
  // Override this if you would like to have your lambda function be
  // Docker-based
  useDockerImageLambda?: boolean;
}

export class InfrastructureStack extends cdk.Stack {
  constructor(
    scope: Construct,
    id: string,
    stackProps: cdk.StackProps,
    signingKeyOptions: SigningKeyOptions
  ) {
    super(scope, id, stackProps);

    // Having a CfnParameter here should ensure that one-click deploy users can pass in their Secret ARN containing
    // their Momento auth token
    const momentoAuthTokenSecretArnParam = new cdk.CfnParameter(
      this,
      'momentoAuthTokenSecretArn',
      {
        type: 'String',
        description:
          'The ARN containing your Momento auth token as plaintext (best practice)',
      }
    );

    InfrastructureStack.validateRotationParams(
      signingKeyOptions.signingKeyTtlMinutes,
      signingKeyOptions.rotateAutomaticallyAfterInDays
    );

    const signingKeyName = signingKeyOptions.momentoSigningKeySecretName
      ? signingKeyOptions.momentoSigningKeySecretName
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
    if (signingKeyOptions.kmsKeyArn !== undefined) {
      lambdaRole.addToPolicy(
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ['kms:Decrypt', 'kms:GenerateDataKey'],
          resources: [signingKeyOptions.kmsKeyArn],
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
            signingKeyOptions.kmsKeyArn
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
        resources: ['*'],
      })
    );
    lambdaRole.addToPolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['secretsmanager:GetSecretValue'],
        resources: ['*'],
      })
    );

    lambdaRole.addToPolicy(
      new PolicyStatement({
        effect: Effect.ALLOW,
        actions: ['cloudwatch:PutMetricData'],
        resources: ['*'],
      })
    );

    const dockerFilePath = path.join(
      __dirname,
      '../../app/signing-key-renewal-lambda/'
    );

    let func: lambda.Function;
    if (signingKeyOptions.useDockerImageLambda) {
      func = new lambda.DockerImageFunction(
        this,
        'momento-signing-key-renewal-lambda-docker',
        {
          code: lambda.DockerImageCode.fromImageAsset(dockerFilePath),
          functionName: 'momento-signing-key-renewal-lambda-docker',
          timeout: Duration.seconds(60),
          memorySize: 512,
          role: lambdaRole,
          environment: {
            MOMENTO_AUTH_TOKEN_SECRET_ARN:
              momentoAuthTokenSecretArnParam.valueAsString,
            EXPORT_METRICS: signingKeyOptions.exportMetrics.toString(),
            SIGNING_KEY_TTL_MINUTES:
              signingKeyOptions.signingKeyTtlMinutes.toString(),
          },
        }
      );
    } else {
      func = new lambda.Function(this, 'momento-signing-key-renewal-lambda', {
        runtime: lambda.Runtime.JAVA_8_CORRETTO,
        code: lambda.Code.fromAsset(
          '../app/signing-key-renewal-lambda/build/libs/signing-key-renewal-lambda-1.0-SNAPSHOT.jar'
        ),
        handler: 'example.Handler',
        functionName: 'momento-signing-key-renewal-lambda',
        timeout: Duration.seconds(60),
        memorySize: 512,
        role: lambdaRole,
        environment: {
          MOMENTO_AUTH_TOKEN_SECRET_ARN:
            momentoAuthTokenSecretArnParam.valueAsString,
          EXPORT_METRICS: signingKeyOptions.exportMetrics.toString(),
          SIGNING_KEY_TTL_MINUTES:
            signingKeyOptions.signingKeyTtlMinutes.toString(),
        },
      });
    }
    if (signingKeyOptions.authTokenKeyValue) {
      func.addEnvironment(
        'AUTH_TOKEN_KEY_VALUE',
        signingKeyOptions.authTokenKeyValue
      );
    }

    func.grantInvoke(new iam.ServicePrincipal('secretsmanager.amazonaws.com'));

    momentoSigningKeySecret.addRotationSchedule('rotation-schedule', {
      rotationLambda: func,
      automaticallyAfter: Duration.days(
        signingKeyOptions.rotateAutomaticallyAfterInDays
      ),
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

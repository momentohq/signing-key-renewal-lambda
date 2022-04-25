import * as cdk from 'aws-cdk-lib';
import {Duration, Fn, Tags} from 'aws-cdk-lib';
import {Construct} from 'constructs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as targets from 'aws-cdk-lib/aws-events-targets';
import * as events from 'aws-cdk-lib/aws-events';
import * as iam from 'aws-cdk-lib/aws-iam';
import {Effect, ManagedPolicy, PolicyStatement, ServicePrincipal} from 'aws-cdk-lib/aws-iam';


export class InfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string) {
    super(scope, id);

    const lambdaRole = new iam.Role(this, `momento-signing-key-renewal-lambda-role`, {
      assumedBy: new ServicePrincipal('lambda.amazonaws.com'),
      managedPolicies: [
        ManagedPolicy.fromManagedPolicyArn(this, `momento-signing-key-renewal-policy`, 'arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole'),
      ]
    });
    lambdaRole.addToPolicy(new PolicyStatement({
      effect: Effect.ALLOW,
      actions: ["secretsmanager:GetSecretValue"],
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
        EXAMPLE_ENV: 'example'
      }
    })

    const target = new targets.LambdaFunction(func, {});
    const event = new events.Rule(this, `momento-signing-key-renewal-event-rule`, {
      schedule: events.Schedule.rate(Duration.days(1)),
      targets: [target]
    })
  }
}

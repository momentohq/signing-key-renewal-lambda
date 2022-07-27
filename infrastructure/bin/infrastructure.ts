import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import {InfrastructureStack} from '../lib/infrastructure-stack';

const DEFAULT_SIGNING_KEY_TTL_MINUTES = '20160'; // 14 days in minutes
const DEFAULT_AUTO_ROTATION_IN_DAYS = '11'; // Rotate automatically in 11 days
const momentoSigningKeySecretName: string | undefined =
  process.env.MOMENTO_SIGNING_KEY_SECRET_NAME;
const singingKeyTtlMinutes: string =
  process.env.SIGNING_KEY_TTL_MINUTES ?? DEFAULT_SIGNING_KEY_TTL_MINUTES;
const autoRotationInDays: string =
  process.env.AUTO_ROTATION_IN_DAYS ?? DEFAULT_AUTO_ROTATION_IN_DAYS;
const exportMetrics: boolean =
  process.env.EXPORT_METRICS?.toLowerCase() === 'true' ?? false;
const kmsKeyArn: string | undefined = process.env.KMS_KEY_ARN;
const authTokenKeyValue: string | undefined = process.env.AUTH_TOKEN_KEY_VALUE;
const useDockerImageLambda: boolean =
  process.env.USE_DOCKER_IMAGE_LAMBDA?.toLowerCase() === 'true' ?? false;

const app = new cdk.App();
new InfrastructureStack(app, 'momento-signing-key-renewal-stack', {
    env: {
      account: process.env.OVERRIDE_ACCOUNT_ID || process.env.CDK_DEFAULT_ACCOUNT,
      region: process.env.OVERRIDE_REGION || process.env.CDK_DEFAULT_REGION,
    },
  },
  {
    momentoSigningKeySecretName: momentoSigningKeySecretName,
    exportMetrics: exportMetrics,
    signingKeyTtlMinutes: parseInt(singingKeyTtlMinutes),
    rotateAutomaticallyAfterInDays: parseInt(autoRotationInDays),
    kmsKeyArn: kmsKeyArn,
    authTokenKeyValue: authTokenKeyValue,
    useDockerImageLambda: useDockerImageLambda,
  });

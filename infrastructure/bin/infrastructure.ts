#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { InfrastructureStack } from '../lib/infrastructure-stack';

const DEFAULT_SIGNING_KEY_TTL_MINUTES = "10080" // 7 days in minutes

const momentoAuthTokenSecretId: string = process.env.MOMENTO_AUTH_SECRET_ID ?? "";
const singingKeyTtlMinutes: string = process.env.SIGNING_KEY_TTL_MINUTES ?? DEFAULT_SIGNING_KEY_TTL_MINUTES;
const exportMetrics: boolean = process.env.EXPORT_METRICS?.toLowerCase() === "true" ? true : false ?? false;
const kmsKeyArn: string | undefined = process.env.KMS_KEY_ARN;

const app = new cdk.App();
new InfrastructureStack(app, `momento-signing-key-renewal-stack`, {
  momentoAuthTokenSecretId: momentoAuthTokenSecretId,
  exportMetrics: exportMetrics,
  signingKeyTtlMinutes: parseInt(singingKeyTtlMinutes),
  kmsKeyArn: kmsKeyArn,
});

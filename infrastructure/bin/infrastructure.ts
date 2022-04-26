#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { InfrastructureStack } from '../lib/infrastructure-stack';

const DEFAULT_SIGNING_KEY_TTL_MINUTES = "10080" // 7 days in minutes
const DEFAULT_RENEW_WITHIN_DAYS = "3" // Renew Momento signing key when its `expiresAt` field is within 3 days

const momentoAuthTokenSecretId: string = process.env.MOMENTO_AUTH_SECRET_ID ?? "";
const momentoAuthTokenSecretKeyName: string = process.env.MOMENTO_AUTH_SECRET_KEY_NAME ?? "";
const momentoSigningKeySecretId: string = process.env.MOMENTO_SIGNING_KEY_SECRET_ID ?? "";
const singingKeyTtlMinutes: string = process.env.SIGNING_KEY_TTL_MINUTES ?? DEFAULT_SIGNING_KEY_TTL_MINUTES;
const renewWithinDays: string = process.env.RENEW_WITHIN_DAYS ?? DEFAULT_RENEW_WITHIN_DAYS;
const exportMetrics: boolean = process.env.EXPORT_METRICS?.toLowerCase() === "true" ? true : false ?? false;

const app = new cdk.App();
new InfrastructureStack(app, `momento-signing-key-renewal-stack`, {
  momentoAuthTokenSecretId: momentoAuthTokenSecretId,
  momentoAuthTokenSecretKeyName: momentoAuthTokenSecretKeyName,
  momentoSigningKeySecretId: momentoSigningKeySecretId,
  exportMetrics: exportMetrics,
  signingKeyTtlMinutes: parseInt(singingKeyTtlMinutes),
  renewWithinDays: parseInt(renewWithinDays),
});

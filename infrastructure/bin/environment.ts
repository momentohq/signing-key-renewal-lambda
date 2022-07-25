import * as child_process from 'child_process';

export interface Environment {
  readonly awsAccountId: string;
  readonly awsRegion: string;
}

export function discoverEnvironment(): Environment {
  const accountId = child_process
    .execSync("aws sts get-caller-identity --output text --query 'Account'")
    .toString()
    .trim();

  if (accountId === '') {
    throw new Error('Unable to determine AWS account id!');
  }

  const region = process.env.AWS_REGION;
  if (region === undefined) {
    throw new Error('Missing required env var AWS_REGION');
  }

  return {
    awsAccountId: accountId,
    awsRegion: region,
  };
}

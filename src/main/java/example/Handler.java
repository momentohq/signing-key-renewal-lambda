package example;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClient;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import momento.sdk.SimpleCacheClient;

import java.util.Map;

import static example.Utils.SecretResultType;

// Handler value: example.Handler
public class Handler implements RequestHandler<Map<String,String>, String> {
  private static final String SECRETS_MANAGER_REGION = "SECRETS_MANAGER_REGION";
  private static final String MOMENTO_AUTH_TOKEN_SECRET_ID = "MOMENTO_AUTH_TOKEN_SECRET_ID";
  private static final String MOMENTO_AUTH_TOKEN_SECRET_KEY_NAME = "MOMENTO_AUTH_TOKEN_SECRET_KEY_NAME";
  private static final String MOMENTO_SIGNING_KEY_SECRET_ID = "MOMENTO_SIGNING_KEY_SECRET_ID";
  private static final String SIGNING_KEY_TTL_MINUTES = "SIGNING_KEY_TTL_MINUTES";
  private static final String RENEW_WITHIN_DAYS = "RENEW_WITHIN_DAYS";
  private static final String KMS_KEY_ARN = "KMS_KEY_ARN";
  private static final String EXPORT_METRICS = "EXPORT_METRICS";

  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  @Override
  public String handleRequest(Map<String,String> event, Context context) {
    LambdaLogger logger = context.getLogger();

    final String awsRegion = System.getenv(SECRETS_MANAGER_REGION);
    final String momentoAuthTokenSecretId = System.getenv(MOMENTO_AUTH_TOKEN_SECRET_ID);
    final String momentoAuthTokenKeyName = System.getenv(MOMENTO_AUTH_TOKEN_SECRET_KEY_NAME);
    final String momentoSigningKeySecretId = System.getenv(MOMENTO_SIGNING_KEY_SECRET_ID);
    final String kmsKeyArn = System.getenv(KMS_KEY_ARN);
    final int signingKeyTtlMinutes = Integer.parseInt(System.getenv(SIGNING_KEY_TTL_MINUTES));
    final int renewWithinDays = Integer.parseInt(System.getenv(RENEW_WITHIN_DAYS));
    final boolean exportMetrics = Boolean.parseBoolean(System.getenv(EXPORT_METRICS));

    AWSSecretsManager secretsManager = AWSSecretsManagerClient.builder()
            .withRegion(awsRegion)
            .build();
    AmazonCloudWatch cloudWatch = AmazonCloudWatchClientBuilder.standard()
            .withRegion(awsRegion)
            .build();
    SimpleCacheClient momentoClient = createSimpleCacheClient(secretsManager, momentoAuthTokenSecretId, momentoAuthTokenKeyName);
    SigningKeyWorkflow signingKeyWorkflow = new SigningKeyWorkflow(logger,
            secretsManager,
            cloudWatch,
            gson,
            momentoClient,
            signingKeyTtlMinutes,
            renewWithinDays,
            kmsKeyArn,
            exportMetrics);
    signingKeyWorkflow.renewSigningKeys(momentoSigningKeySecretId);
    return "";
  }

  private SimpleCacheClient createSimpleCacheClient(AWSSecretsManager secretsManager,
                                                    String momentoAuthTokenSecretId,
                                                    String momentoAuthTokenKeyName) {
    if (StringUtils.isNullOrEmpty(momentoAuthTokenSecretId)) {
      throw new RuntimeException("MOMENTO_AUTH_TOKEN_SECRET_ID was empty");
    }
    // This assumes your Momento auth token is stored as a pure
    String momentoAuthTokenSecret = Utils.getSecretValueString(secretsManager, momentoAuthTokenSecretId);
    Map<String, String> secretJson = gson.fromJson(momentoAuthTokenSecret, SecretResultType);
    String momentoAuthToken = secretJson.get(momentoAuthTokenKeyName);
    return SimpleCacheClient.builder(momentoAuthToken, 300).build();
  }
}

package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.util.StringUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import example.awsclients.AwsClientsFactory;
import example.awsclients.cloudwatch.CloudWatch;
import example.awsclients.secretsmanager.SecretsManager;
import momento.sdk.SimpleCacheClient;
import momento.sdk.exceptions.InvalidArgumentException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Handler entry point: example.Handler
public class Handler implements RequestHandler<Map<String,String>, String> {
  private static final String MOMENTO_AUTH_TOKEN_SECRET_ARN = "MOMENTO_AUTH_TOKEN_SECRET_ARN";
  private static final String SIGNING_KEY_TTL_MINUTES = "SIGNING_KEY_TTL_MINUTES";
  private static final String EXPORT_METRICS = "EXPORT_METRICS";
  private static final String USE_LOCAL_STUBS = "USE_LOCAL_STUBS";
  private final List<String> environmentVariableNames = Arrays.asList(
          MOMENTO_AUTH_TOKEN_SECRET_ARN,
          SIGNING_KEY_TTL_MINUTES,
          EXPORT_METRICS
  );

  private final Gson gson = new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .setPrettyPrinting()
          .create();

  private void validateEnvironment() {
    final Map<String, String> env = System.getenv();
    for (String envVariable : environmentVariableNames) {
      if (!env.containsKey(envVariable) || StringUtils.isNullOrEmpty(env.get(envVariable))) {
        throw new InvalidArgumentException(String.format("Expected %s to have a value but none was provided", envVariable));
      }
    }
  }

  @Override
  public String handleRequest(Map<String,String> event, Context context) {
    LambdaLogger logger = context.getLogger();
    validateEnvironment();
    final String momentoAuthTokenSecretArn = System.getenv(MOMENTO_AUTH_TOKEN_SECRET_ARN);
    final int signingKeyTtlMinutes = Integer.parseInt(System.getenv(SIGNING_KEY_TTL_MINUTES));
    final boolean exportMetrics = Boolean.parseBoolean(System.getenv(EXPORT_METRICS));
    final boolean shouldUseLocalStubs = shouldUseLocalStubs();

    SecretsManager secretsManager = AwsClientsFactory.getSecretsManagerClient(shouldUseLocalStubs, logger);
    CloudWatch cloudWatch = AwsClientsFactory.getCloudWatchClient(shouldUseLocalStubs, logger);
    try (SimpleCacheClient momentoClient = createSimpleCacheClient(secretsManager,momentoAuthTokenSecretArn)) {
      RotationWorkflow rotationWorkflow = new RotationWorkflow(logger,
              secretsManager,
              cloudWatch,
              gson,
              momentoClient,
              signingKeyTtlMinutes,
              exportMetrics);
      rotationWorkflow.processRotation(event);
      return "";
    }
  }

  private SimpleCacheClient createSimpleCacheClient(SecretsManager secretsManager,
                                                    String momentoAuthTokenSecretId) {
    // This assumes your Momento auth token is stored as a pure
    String momentoAuthToken = secretsManager.getSecretValueString(momentoAuthTokenSecretId, null, null);
    return SimpleCacheClient.builder(momentoAuthToken, 300).build();
  }

  private boolean shouldUseLocalStubs() {
    Optional<String> maybeUseLocalStubs = Optional.ofNullable(System.getenv(USE_LOCAL_STUBS));
    return maybeUseLocalStubs.filter(Boolean::parseBoolean).isPresent();
  }
}


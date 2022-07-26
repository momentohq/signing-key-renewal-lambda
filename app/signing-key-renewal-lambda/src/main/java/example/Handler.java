package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import com.amazonaws.util.StringUtils;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
  private static final String AUTH_TOKEN_KEY_VALUE = "AUTH_TOKEN_KEY_VALUE";
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
    if (isAutomaticRotationWorkflow(event)) {
      automaticRotationWorkflow(event, context);
    } else {
      manualRotationWorkflow(event, context);
    }
    return "";
  }

  private void automaticRotationWorkflow(Map<String, String> event, Context context) {
    LambdaLogger logger = context.getLogger();
    validateEnvironment();
    final String momentoAuthTokenSecretArn = System.getenv(MOMENTO_AUTH_TOKEN_SECRET_ARN);
    final String authTokenKeyValue = System.getenv(AUTH_TOKEN_KEY_VALUE);
    final int signingKeyTtlMinutes = Integer.parseInt(System.getenv(SIGNING_KEY_TTL_MINUTES));
    final boolean exportMetrics = Boolean.parseBoolean(System.getenv(EXPORT_METRICS));
    final boolean shouldUseLocalStubs = shouldUseLocalStubs();

    SecretsManager secretsManager = AwsClientsFactory.getSecretsManagerClient(shouldUseLocalStubs, logger);
    CloudWatch cloudWatch = AwsClientsFactory.getCloudWatchClient(shouldUseLocalStubs, logger);
    try (SimpleCacheClient momentoClient = createSimpleCacheClient(secretsManager, momentoAuthTokenSecretArn, authTokenKeyValue)) {
      RotationWorkflow rotationWorkflow = new RotationWorkflow(logger,
              secretsManager,
              cloudWatch,
              gson,
              momentoClient,
              signingKeyTtlMinutes,
              exportMetrics);
      rotationWorkflow.processRotation(event);
    }
  }

  private void manualRotationWorkflow(Map<String, String> event, Context context) {
    LambdaLogger logger = context.getLogger();
    logger.log("Received event: " + event.toString());
    final Optional<String> momentoAuthToken = Optional.ofNullable(event.getOrDefault("momento_auth_token", null));
    final Optional<String> momentoSigningKeySecretName = Optional.ofNullable(event.getOrDefault("momento_signing_key_secret_name", null));
    if (!momentoAuthToken.isPresent()) {
      throw new IllegalArgumentException("Presumed manual rotation workflow, momento_auth_token is required.");
    }
    if (!momentoSigningKeySecretName.isPresent()) {
      throw new IllegalArgumentException("Presumed manual rotation workflow, momento_signing_key_secret_name is required.");
    }
    logger.log(momentoSigningKeySecretName.get());
    final int signingKeyTtlMinutes = Integer.parseInt(event.getOrDefault("signing_key_ttl_minutes", "20160" ));
    final boolean exportMetrics = Boolean.parseBoolean(event.getOrDefault("export_metrics", "false" ));
    final boolean shouldUseLocalStubs = Boolean.parseBoolean(event.getOrDefault("use_local_stubs", "false" ));

    SecretsManager secretsManager = AwsClientsFactory.getSecretsManagerClient(shouldUseLocalStubs, logger);
    CloudWatch cloudWatch = AwsClientsFactory.getCloudWatchClient(shouldUseLocalStubs, logger);
    try (SimpleCacheClient momentoClient = SimpleCacheClient.builder(momentoAuthToken.get(), 300).build()) {
      RotationWorkflow rotationWorkflow = new RotationWorkflow(logger,
              secretsManager,
              cloudWatch,
              gson,
              momentoClient,
              signingKeyTtlMinutes,
              exportMetrics);
      rotationWorkflow.manualRotation(momentoSigningKeySecretName.get());
    }
  }

  private SimpleCacheClient createSimpleCacheClient(SecretsManager secretsManager,
                                                    String momentoAuthTokenSecretId,
                                                    String authTokenKeyValue) {
    String momentoAuthToken = secretsManager.getSecretValueString(momentoAuthTokenSecretId, null, null);
    // If your token is stored like this: '{"token": "<momento auth token value>"}',
    // you would pass in "token" for authTokenKeyValue.
    // Otherwise, this will presume your token is stored as a simple string
    Optional<String> maybeAuthTokenKeyValue = Optional.ofNullable(authTokenKeyValue);
    if (maybeAuthTokenKeyValue.isPresent()) {
      JsonElement element = gson.fromJson(momentoAuthToken, JsonElement.class);
      JsonObject obj = element.getAsJsonObject();
      String parsedToken = obj.get(maybeAuthTokenKeyValue.get()).getAsString();
      return SimpleCacheClient.builder(parsedToken, 300).build();
    }
    return SimpleCacheClient.builder(momentoAuthToken, 300).build();
  }

  private boolean shouldUseLocalStubs() {
    Optional<String> maybeUseLocalStubs = Optional.ofNullable(System.getenv(USE_LOCAL_STUBS));
    return maybeUseLocalStubs.filter(Boolean::parseBoolean).isPresent();
  }

  private boolean isAutomaticRotationWorkflow(Map<String, String> event) {
    return event.containsKey("ClientRequestToken") && event.containsKey("SecretId") && event.containsKey("Step");
  }
}


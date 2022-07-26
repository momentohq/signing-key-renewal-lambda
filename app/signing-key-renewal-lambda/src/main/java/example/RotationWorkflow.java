package example;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import example.awsclients.cloudwatch.CloudWatch;
import example.awsclients.secretsmanager.SecretsManager;
import momento.sdk.SimpleCacheClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RotationWorkflow {
    private final LambdaLogger logger;
    private final SecretsManager secretsManager;
    private final CloudWatch cloudWatch;
    private final Gson gson;
    private final SimpleCacheClient momentoClient;
    private final int signingKeyTtlMinutes;
    private final boolean exportMetrics;

    public RotationWorkflow(LambdaLogger logger,
                            SecretsManager secretsManager,
                            CloudWatch cloudWatch,
                            Gson gson,
                            SimpleCacheClient momentoClient,
                            int signingKeyTtlMinutes,
                            boolean exportMetrics) {
        this.logger = logger;
        this.secretsManager = secretsManager;
        this.cloudWatch = cloudWatch;
        this.gson = gson;
        this.momentoClient = momentoClient;
        this.signingKeyTtlMinutes = signingKeyTtlMinutes;
        this.exportMetrics = exportMetrics;
    }

    public void manualRotation(String secretName) {
        try {
            secretsManager.getSecretValueString(secretName, null, null);
        } catch (ResourceNotFoundException e) {
            this.logger.log(String.format("%s not found, creating new secret", secretName));
            secretsManager.createSecret(secretName, null, null);
        }
        MomentoSigningKey signingKey = MomentoSigningKey.fromCreateSigningResponse(this.momentoClient.createSigningKey(this.signingKeyTtlMinutes));
        String serializedSigningKey = this.gson.toJson(signingKey);
        secretsManager.putSecretValue(secretName, serializedSigningKey, null, null);
        this.logger.log(String.format("Signing key rotated for %s", secretName));
    }

    public void processRotation(Map<String, String> event) {
        String arn = event.get("SecretId");
        String token = event.get("ClientRequestToken");
        String step = event.get("Step");

        if (StringUtils.isNullOrEmpty(arn) || StringUtils.isNullOrEmpty(token) || StringUtils.isNullOrEmpty(step)) {
            throw new IllegalStateException(
                    String.format("Event given was malformed, expected SecretId (%s), ClientRequestToken (%s), " +
                            "and Step (%s) to have values", arn, token, step)
            );
        }

        Map<String, List<String>> versionStages = secretsManager.getVersionStages(arn);
        logger.log(versionStages.toString());
        if (!versionStages.containsKey(token)) {
            throw new IllegalArgumentException(String.format("Secret version %s has no stage for rotation of secret %s.",
                    token, arn));
        }
        if (versionStages.get(token).contains(VERSION_STAGES.AWSCURRENT.toString())) {
            this.logger.log(String.format("Secret version %s already set as AWSCURRENT for secret %s.", token, arn));
            return;
        } else if (!versionStages.get(token).contains(VERSION_STAGES.AWSPENDING.toString())) {
            throw new IllegalArgumentException(String.format("Secret version %s not set as AWSPENDING for rotation of secret %s.",
                    token, arn));
        }

        switch(step) {
            case "createSecret": {
                createSecret(arn, token);
                break;
            }
            case "setSecret": {
                setSecret();
                break;
            }
            case "testSecret": {
                testSecret();
                break;
            }
            case "finishSecret": {
                finishSecret(arn, token);
                break;
            }
            default: {
                throw new IllegalArgumentException(String.format("Invalid step parameter: %s", step));
            }
        }
    }

    private void createSecret(String arn, String token) {
        // Ensure our secret exists first, this call should not throw an exception
        secretsManager.getSecretValueString(arn, null, VERSION_STAGES.AWSCURRENT.toString());

        // Attempt to retrieve the secret version. If this fails, put a new secret
        try {
            secretsManager.getSecretValueString(arn, token, VERSION_STAGES.AWSPENDING.toString());
            this.logger.log(String.format("createSecret: Successfully retrieved secret for %s", arn));
        } catch (ResourceNotFoundException e) {
            // Get our updated signing key
            MomentoSigningKey signingKey = MomentoSigningKey.fromCreateSigningResponse(this.momentoClient.createSigningKey(this.signingKeyTtlMinutes));
            String serializedSigningKey = this.gson.toJson(signingKey);
            secretsManager.putSecretValue(arn, serializedSigningKey, token, Collections.singletonList(VERSION_STAGES.AWSPENDING.toString()));
            this.logger.log(String.format("createSecret: Successfully put secret for ARN %s and version %s", arn, token));
            if (this.exportMetrics) {
                cloudWatch.putMetricData(signingKey.getExpiresAt());
                this.logger.log("createSecret: exported metrics with updated time until expiration");
            }
        }
    }

    private void setSecret() {
        // No-op, we don't have anywhere in the service to set a secret
    }

    private void testSecret() {
        // No-op, we don't require testing the secret anywhere against the service
    }

    private void finishSecret(String arn, String token) {
        // First describe the secret to get the current version
        Map<String, List<String>> versionStages = secretsManager.getVersionStages(arn);
        Optional<String> currentVersion = Optional.empty();
        for (Map.Entry<String, List<String>> version: versionStages.entrySet()) {
            if (versionStages.get(version.getKey()).contains(VERSION_STAGES.AWSCURRENT.toString())) {
                if (token.equals(version.getKey())) {
                    this.logger.log(String.format("finishSecret: Version %s already marked as AWSCURRENT for %s", version.getKey(), arn));
                    return;
                }
                currentVersion = Optional.of(version.getKey());
                break;
            }
        }
        // Finalize by staging the secret version current
        secretsManager.updateSecretVersionStage(arn, VERSION_STAGES.AWSCURRENT.toString(), token, currentVersion.orElse(null));
        this.logger.log(String.format("finishSecret: Successfully set AWSCURRENT stage to version %s for secret %s", token, arn));
    }

    private enum VERSION_STAGES {
        AWSCURRENT, AWSPENDING
    }
}

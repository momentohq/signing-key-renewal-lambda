package example.awsclients.secretsmanager;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.List;
import java.util.Map;

public class LocalSecretsMangerClient implements SecretsManager {
    public LocalSecretsMangerClient(LambdaLogger logger, String getSecretValueStringReturnOverride, Map<String, List<String>> getVersionStagesReturnOverride) {
        this.logger = logger;
        this.getSecretValueStringReturnOverride = getSecretValueStringReturnOverride;
        this.getVersionStagesReturnOverride = getVersionStagesReturnOverride;
    }

    private final LambdaLogger logger;
    private final String getSecretValueStringReturnOverride;
    private final Map<String, List<String>> getVersionStagesReturnOverride;

    @Override
    public String getSecretValueString(String secretId, String versionId, String versionStage) {
        this.logger.log("Called getSecretValueString");
        return getSecretValueStringReturnOverride;
    }

    @Override
    public void createSecret(String secretId, String secretString, String kmsKeyArn) {
        this.logger.log("Called createSecret");
    }

    @Override
    public Map<String, List<String>> getVersionStages(String secretId) {
        this.logger.log("Called getVersionStages");
        return getVersionStagesReturnOverride;
    }

    @Override
    public void putSecretValue(String secretId, String newSecretString, String token, List<String> versionStages) {
        this.logger.log("Called putSecretValue");
    }

    @Override
    public void updateSecretVersionStage(String secretId, String versionStage, String moveToVersionId, String removeFromVersionId) {
        this.logger.log("Called updateSecretValue");
    }
}

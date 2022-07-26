package example.awsclients.secretsmanager;

import java.util.List;
import java.util.Map;

public interface SecretsManager {
    String getSecretValueString(String secretId, String versionId, String versionStage);
    void createSecret(String secretId, String secretString, String kmsKeyArn);
    Map<String, List<String>> getVersionStages(String secretId);
    void putSecretValue(String secretId, String newSecretString, String token, List<String> versionStages);
    void updateSecretVersionStage(String secretId, String versionStage, String moveToVersionId, String removeFromVersionId);
}

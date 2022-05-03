package example.awsclients.secretsmanager;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.DescribeSecretRequest;
import com.amazonaws.services.secretsmanager.model.DescribeSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.PutSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.UpdateSecretVersionStageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SecretsManagerClient implements SecretsManager {
    private final AWSSecretsManager awsSecretsManager;
    public SecretsManagerClient(AWSSecretsManager secretsManager) {
        this.awsSecretsManager = secretsManager;
    }
    @Override
    public String getSecretValueString(String secretId, String versionId, String versionStage) {
        Optional<String> maybeVersionId = Optional.ofNullable(versionId);
        Optional<String> maybeVersionStage = Optional.ofNullable(versionStage);
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretId);
        maybeVersionId.ifPresent(getSecretValueRequest::withVersionId);
        maybeVersionStage.ifPresent(getSecretValueRequest::withVersionStage);
        return awsSecretsManager.getSecretValue(getSecretValueRequest).getSecretString();
    }

    @Override
    public void createSecret(String secretId, String secretString, String kmsKeyArn) {
        CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                .withName(secretId)
                .withDescription("Stores a serialized Momento signing key to create presigned URLs")
                .withSecretString(secretString);
        Optional<String> maybeKeyArn = Optional.ofNullable(kmsKeyArn);
        maybeKeyArn.ifPresent(createSecretRequest::withKmsKeyId);
        awsSecretsManager.createSecret(createSecretRequest);    }

    @Override
    public Map<String, List<String>> getVersionStages(String secretId) {
        DescribeSecretRequest describeSecretRequest = new DescribeSecretRequest()
                .withSecretId(secretId);
        DescribeSecretResult describeSecretResult = awsSecretsManager.describeSecret(describeSecretRequest);
        if (!describeSecretResult.isRotationEnabled()) {
            throw new IllegalArgumentException(String.format("Secret %s is not enabled for rotation", secretId));
        }
        return describeSecretResult.getVersionIdsToStages();
    }

    @Override
    public void putSecretValue(String secretId, String newSecretString, String token, List<String> versionStages) {
        Optional<String> maybeToken = Optional.ofNullable(token);
        Optional<List<String>> maybeVersionStages = Optional.ofNullable(versionStages);
        PutSecretValueRequest putSecretValueRequest = new PutSecretValueRequest()
                .withSecretId(secretId)
                .withSecretString(newSecretString);
        maybeToken.ifPresent(putSecretValueRequest::withClientRequestToken);
        maybeVersionStages.ifPresent(putSecretValueRequest::withVersionStages);
        awsSecretsManager.putSecretValue(putSecretValueRequest);
    }

    @Override
    public void updateSecretVersionStage(String secretId, String versionStage, String moveToVersionId, String removeFromVersionId) {
        Optional<String> maybeRemoveFromVersionId = Optional.ofNullable(removeFromVersionId);
        UpdateSecretVersionStageRequest updateSecretVersionStageRequest = new UpdateSecretVersionStageRequest()
                .withSecretId(secretId)
                .withVersionStage(versionStage)
                .withMoveToVersionId(moveToVersionId);
        maybeRemoveFromVersionId.ifPresent(updateSecretVersionStageRequest::withRemoveFromVersionId);
        awsSecretsManager.updateSecretVersionStage(updateSecretVersionStageRequest);
    }
}

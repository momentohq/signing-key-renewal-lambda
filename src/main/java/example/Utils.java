package example;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.PutSecretValueRequest;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

public class Utils {

    public static String getSecretValueString(AWSSecretsManager awsSecretsManager, String secretId) {
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretId);
        return awsSecretsManager.getSecretValue(getSecretValueRequest).getSecretString();
    }

    public static void createSecret(AWSSecretsManager awsSecretsManager,
                             String secretId,
                             String secretString,
                             String kmsKeyArn) {
        CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                .withName(secretId)
                .withDescription("Stores a serialized Momento signing key to create presigned URLs")
                .withSecretString(secretString);
        Optional<String> maybeKeyArn = Optional.of(kmsKeyArn);
        maybeKeyArn.ifPresent(createSecretRequest::withKmsKeyId);
        awsSecretsManager.createSecret(createSecretRequest);
    }

    public static void updateSecretValue(AWSSecretsManager awsSecretsManager, String secretId, String newSecretString) {
        PutSecretValueRequest putSecretValueRequest = new PutSecretValueRequest()
                .withSecretId(secretId)
                .withSecretString(newSecretString);
        awsSecretsManager.putSecretValue(putSecretValueRequest);
    }
    public static Type SecretResultType = new TypeToken<Map<String, String>>() {}.getType();
}

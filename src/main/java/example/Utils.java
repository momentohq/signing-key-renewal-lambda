package example;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.CreateSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest;
import com.amazonaws.services.secretsmanager.model.UpdateSecretResult;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class Utils {
    public static String getSecretValueString(AWSSecretsManager awsSecretsManager, String secretId) {
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(secretId);
        return awsSecretsManager.getSecretValue(getSecretValueRequest).getSecretString();
    }

    public static CreateSecretResult createSecret(AWSSecretsManager awsSecretsManager, String secretId, String secretString) {
        CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                .withName(secretId)
                .withSecretString(secretString);
        return awsSecretsManager.createSecret(createSecretRequest);
    }

    public static UpdateSecretResult updateSecret(AWSSecretsManager awsSecretsManager, String secretId, String newSecretString) {
        UpdateSecretRequest updateSecretRequest = new UpdateSecretRequest()
                .withSecretId(secretId)
                .withSecretString(newSecretString);
        return awsSecretsManager.updateSecret(updateSecretRequest);
    }
    public static Type SecretResultType = new TypeToken<Map<String, String>>() {}.getType();

}

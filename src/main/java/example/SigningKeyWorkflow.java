package example;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import momento.sdk.SimpleCacheClient;
import momento.sdk.messages.CreateSigningKeyResponse;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static example.Utils.SecretResultType;

public class SigningKeyWorkflow {
    private final LambdaLogger logger;
    private final AWSSecretsManager secretsManager;
    private final Gson gson;
    private final SimpleCacheClient momentoClient;
    private final int signingKeyTtlMinutes;
    private final int daysUntilRenewal;

    public SigningKeyWorkflow(LambdaLogger logger,
                              AWSSecretsManager secretsManager,
                              Gson gson,
                              SimpleCacheClient momentoClient,
                              int signingKeyTtlMinutes,
                              int daysUntilRenewal) {
        this.logger = logger;
        this.secretsManager = secretsManager;
        this.gson = gson;
        this.momentoClient = momentoClient;
        this.signingKeyTtlMinutes = signingKeyTtlMinutes;
        this.daysUntilRenewal = daysUntilRenewal;
    }

    public void renewSigningKeys(String signingKeySecretId) {
        try {
            String secretValueString = Utils.getSecretValueString(this.secretsManager, signingKeySecretId);
            CreateSigningKeyResponse signingKeyMetadata = extractSigningKeyInformation(secretValueString);
            if (isEligibleForRenewal(signingKeyMetadata)) {
                this.logger.log("Signing Key ID " + signingKeyMetadata.getKeyId() + " eligible for renewal");
                Map<String, String> serializedSecret = renewSigningKey();
                Utils.updateSecret(this.secretsManager, signingKeySecretId, gson.toJson(serializedSecret));
                this.logger.log("Signing key renewed");
            } else {
                this.logger.log("Signing key not eligible for renewal yet");
            }
        } catch (ResourceNotFoundException e) {
            this.logger.log("SecretId " + signingKeySecretId + " does not exist, creating key");
            Map<String, String> serializedSecret = renewSigningKey();
            Utils.createSecret(this.secretsManager, signingKeySecretId, gson.toJson(serializedSecret));
            this.logger.log("Secret created with new signing key");
        }
    }

    private CreateSigningKeyResponse extractSigningKeyInformation(String secretValueString) {
        Map<String, String> jsonString = this.gson.fromJson(secretValueString, SecretResultType);
        return this.gson.fromJson(jsonString.get("signingKey"), CreateSigningKeyResponse.class);
    }

    private Map<String, String> renewSigningKey() {
        CreateSigningKeyResponse response = this.momentoClient.createSigningKey(this.signingKeyTtlMinutes);
        Map<String, String> map = new HashMap<>();
        map.put("signingKey", gson.toJson(response));
        return map;
    }

    private boolean isEligibleForRenewal(CreateSigningKeyResponse existingSigningKey) {
        Calendar timeRangeToCheckFor = Calendar.getInstance();
        timeRangeToCheckFor.add(Calendar.DATE, this.daysUntilRenewal);
        // We have to convert from a Date to a Calendar instance so that .after() functions as expected
        Calendar timeKeyExpires = Calendar.getInstance();
        timeKeyExpires.setTime(existingSigningKey.getExpiresAt());
        return timeRangeToCheckFor.after(timeKeyExpires);
    }
}

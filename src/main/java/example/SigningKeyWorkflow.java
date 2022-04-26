package example;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import momento.sdk.SimpleCacheClient;
import momento.sdk.messages.CreateSigningKeyResponse;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static example.Utils.SecretResultType;

public class SigningKeyWorkflow {
    private final LambdaLogger logger;
    private final AWSSecretsManager secretsManager;
    private final AmazonCloudWatch cloudWatch;
    private final Gson gson;
    private final SimpleCacheClient momentoClient;
    private final int signingKeyTtlMinutes;
    private final int daysUntilRenewal;
    private final String kmsKeyArn;
    private final boolean exportMetrics;

    public SigningKeyWorkflow(LambdaLogger logger,
                              AWSSecretsManager secretsManager,
                              AmazonCloudWatch cloudWatch,
                              Gson gson,
                              SimpleCacheClient momentoClient,
                              int signingKeyTtlMinutes,
                              int daysUntilRenewal,
                              String kmsKeyArn,
                              boolean exportMetrics) {
        this.logger = logger;
        this.secretsManager = secretsManager;
        this.cloudWatch = cloudWatch;
        this.gson = gson;
        this.momentoClient = momentoClient;
        this.signingKeyTtlMinutes = signingKeyTtlMinutes;
        this.daysUntilRenewal = daysUntilRenewal;
        this.exportMetrics = exportMetrics;
        if (StringUtils.isNullOrEmpty(kmsKeyArn)) {
            // We want this to be null in case we call Utils.createSecret() and want to use
            // a specific KMS key
            this.kmsKeyArn = null;
        } else {
            this.kmsKeyArn = kmsKeyArn;
        }
    }

    public void renewSigningKeys(String signingKeySecretId) {
        // We'll use this for exporting metrics (if enabled)
        Date expiresAt = null;
        try {
            String secretValueString = Utils.getSecretValueString(this.secretsManager, signingKeySecretId);
            CreateSigningKeyResponse signingKeyMetadata = extractSigningKeyInformation(secretValueString);
            expiresAt = signingKeyMetadata.getExpiresAt();
            if (isEligibleForRenewal(signingKeyMetadata)) {
                this.logger.log("Signing Key ID " + signingKeyMetadata.getKeyId() + " eligible for renewal");
                MomentoSigningKey signingKey = createMomentoSigningKey();
                // Update expiresAt with our new value
                expiresAt = signingKey.getCreateSigningKeyResponse().getExpiresAt();
                Utils.updateSecretValue(this.secretsManager, signingKeySecretId, gson.toJson(signingKey.getJsonMap()));
                this.logger.log("Signing key renewed");
            } else {
                this.logger.log("Signing key not eligible for renewal yet");
            }
        } catch (ResourceNotFoundException e) {
            // Handles the case in which the SecretId hasn't been created in SecretsManager yet
            this.logger.log("SecretId " + signingKeySecretId + " does not exist, creating key");
            MomentoSigningKey signingKey = createMomentoSigningKey();
            expiresAt = signingKey.getCreateSigningKeyResponse().getExpiresAt();
            Utils.createSecret(this.secretsManager, signingKeySecretId, gson.toJson(signingKey.getJsonMap()), this.kmsKeyArn);
            this.logger.log("Secret created with new signing key");
        }
        // If we've enabled metrics, export them
        if (null != expiresAt && exportMetrics) {
            exportMetrics(expiresAt);
        }
    }

    private CreateSigningKeyResponse extractSigningKeyInformation(String secretValueString) {
        Map<String, String> jsonString = this.gson.fromJson(secretValueString, SecretResultType);
        return this.gson.fromJson(jsonString.get("signingKey"), CreateSigningKeyResponse.class);
    }

    private MomentoSigningKey createMomentoSigningKey() {
        CreateSigningKeyResponse response = this.momentoClient.createSigningKey(this.signingKeyTtlMinutes);
        Map<String, String> map = new HashMap<>();
        map.put("signingKey", gson.toJson(response));
        return new MomentoSigningKey(response, map);
    }

    private boolean isEligibleForRenewal(CreateSigningKeyResponse existingSigningKey) {
        Calendar timeRangeToCheckFor = Calendar.getInstance();
        timeRangeToCheckFor.add(Calendar.DATE, this.daysUntilRenewal);
        // We have to convert from a Date to a Calendar instance so that .after() functions as expected
        Calendar timeKeyExpires = Calendar.getInstance();
        timeKeyExpires.setTime(existingSigningKey.getExpiresAt());
        return timeRangeToCheckFor.after(timeKeyExpires);
    }

    // Export CW metrics to account indicating how long until the given Momento signing key needs to be renewed.
    // This will only be called if EXPORT_METRICS is true
    private void exportMetrics(Date expiresAt) {
        Date now = new Date();
        double differenceInSeconds = TimeUnit.MILLISECONDS.toSeconds(expiresAt.getTime() - now.getTime());
        this.logger.log("Time difference " + differenceInSeconds);
        MetricDatum datum = new MetricDatum()
                .withMetricName("time_until_signing_key_expires_seconds")
                .withUnit(StandardUnit.Seconds)
                .withValue(differenceInSeconds);
        PutMetricDataRequest request = new PutMetricDataRequest()
                .withNamespace("Momento/SigningKeyRenewalLambda")
                .withMetricData(datum);
        this.cloudWatch.putMetricData(request);
        this.logger.log("Exported metrics with latest time until expiry");
    }
}

package example.awsclients;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClient;
import example.awsclients.cloudwatch.CloudWatch;
import example.awsclients.cloudwatch.CloudWatchClient;
import example.awsclients.cloudwatch.LocalCloudWatchClient;
import example.awsclients.secretsmanager.LocalSecretsMangerClient;
import example.awsclients.secretsmanager.SecretsManager;
import example.awsclients.secretsmanager.SecretsManagerClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AwsClientsFactory {
    public static SecretsManager getSecretsManagerClient(boolean isDockerEnv, LambdaLogger logger) {
        if (isDockerEnv) {
            String localMomentoToken = System.getenv("TEST_AUTH_TOKEN");
            Map<String, List<String>> versionStagesOverride = new HashMap<>();
            versionStagesOverride.put("a", Collections.singletonList("AWSPREVIOUS"));
            versionStagesOverride.put("b", Collections.singletonList("AWSCURRENT"));
            versionStagesOverride.put("c", Collections.singletonList("AWSPENDING"));
            return new LocalSecretsMangerClient(logger, localMomentoToken, versionStagesOverride);
        }
        return new SecretsManagerClient(AWSSecretsManagerClient.builder().build());
    }

    public static CloudWatch getCloudWatchClient(boolean isDockerEnv, LambdaLogger logger) {
        if (isDockerEnv) {
            return new LocalCloudWatchClient(logger);
        }
        return new CloudWatchClient(AmazonCloudWatchClientBuilder.standard()
                .build());
    }
}

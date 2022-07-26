package example.awsclients.cloudwatch;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Date;

public class LocalCloudWatchClient implements CloudWatch {
    private final LambdaLogger logger;

    public LocalCloudWatchClient(LambdaLogger logger) {
        this.logger = logger;
    }

    @Override
    public void putMetricData(Date expiresAt) {
        this.logger.log("No-op, called cloudWatch.putMetricData()" );
    }
}

package example.awsclients.cloudwatch;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Date;

public interface CloudWatch {
    // Export CW metrics to account indicating how long until the given Momento signing key needs to be renewed.
    // This will only be called if EXPORT_METRICS is true
    public void putMetricData(Date expiresAt);
}


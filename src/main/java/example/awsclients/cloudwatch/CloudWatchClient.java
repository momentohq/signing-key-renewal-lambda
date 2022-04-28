package example.awsclients.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class CloudWatchClient implements CloudWatch {
    private final AmazonCloudWatch cloudWatch;

    public CloudWatchClient(AmazonCloudWatch cloudWatch) {
        this.cloudWatch = cloudWatch;
    }

    @Override
    public void putMetricData(Date expiresAt) {
        Date now = new Date();
        double differenceInSeconds = TimeUnit.MILLISECONDS.toSeconds(expiresAt.getTime() - now.getTime());
        MetricDatum datum = new MetricDatum()
                .withMetricName("time_until_signing_key_expires_seconds")
                .withUnit(StandardUnit.Seconds)
                .withValue(differenceInSeconds);
        PutMetricDataRequest request = new PutMetricDataRequest()
                .withNamespace("Momento/SigningKeyRenewalLambda")
                .withMetricData(datum);
        cloudWatch.putMetricData(request);
    }
}

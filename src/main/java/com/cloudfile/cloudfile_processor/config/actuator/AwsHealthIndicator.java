package com.cloudfile.cloudfile_processor.config.actuator;

import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Component
public class AwsHealthIndicator implements HealthIndicator {

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;


    @Override
    public Health health() {
        Map<String, String> details = new HashMap<>();
        boolean isUp = true;

        try {
            s3Client.listBuckets();
            details.put("S3", "UP");
        } catch (Exception e) {
            details.put("S3", "DOWN: " + e.getMessage());
            isUp = false;
        }

        try {
            dynamoDbClient.listTables();
            details.put("DynamoDB", "UP");
        } catch (Exception e) {
            details.put("DynamoDB", "DOWN: " + e.getMessage());
            isUp = false;
        }

        try {
            sqsClient.listQueues();
            details.put("SQS", "UP");
        } catch (Exception e) {
            details.put("SQS", "DOWN: " + e.getMessage());
            isUp = false;
        }

        if (isUp) {
            return Health.up().withDetails(details).build();
        } else {
            return Health.down().withDetails(details).build();
        }
    }
}

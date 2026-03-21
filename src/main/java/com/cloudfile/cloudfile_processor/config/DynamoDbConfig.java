package com.cloudfile.cloudfile_processor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(AwsProperties awsProperties) {
        return DynamoDbClient.builder()
                .region(Region.of(awsProperties.region()))
                .build();
    }
}

package com.cloudfile.cloudfile_processor.config;

import com.cloudfile.cloudfile_processor.model.FileMetadata;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;


@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(AwsProperties awsProperties) {
        return DynamoDbClient.builder()
                .region(Region.of(awsProperties.getRegion()))
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<FileMetadata> fileTable(
            DynamoDbEnhancedClient client,
            DynamoDbProperties dynamoDbProperties
    ) {
        return client.table(
                dynamoDbProperties.getFilesTableName(),
                TableSchema.fromBean(FileMetadata.class)
        );
    }

}

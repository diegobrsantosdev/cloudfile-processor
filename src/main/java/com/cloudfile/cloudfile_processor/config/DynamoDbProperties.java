package com.cloudfile.cloudfile_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dynamodb")
public record DynamoDbProperties(
        String filesTableName
) {}

package com.cloudfile.cloudfile_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sqs")
public record SqsProperties(
        String queueUrl
) {}

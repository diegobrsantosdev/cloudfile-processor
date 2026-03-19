package com.cloudfile.cloudfile_processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws")
public record AwsProperties(
        String region
) {
}

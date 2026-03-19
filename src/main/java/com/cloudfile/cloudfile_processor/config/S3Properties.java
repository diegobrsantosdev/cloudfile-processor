package com.cloudfile.cloudfile_processor.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String inputBucketName,
        String outputBucketName,
        Integer presignedUrlExpirationMinutes
) {
}

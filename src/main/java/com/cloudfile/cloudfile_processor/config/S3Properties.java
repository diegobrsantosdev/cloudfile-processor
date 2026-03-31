package com.cloudfile.cloudfile_processor.config;


import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class S3Properties {

    @Value("${s3.input-bucket-name}")
    private String inputBucketName;

    @Value("${s3.output-bucket-name}")
    private String outputBucketName;

    @Value("${s3.presigned-url-expiration-minutes}")
    private Integer presignedUrlExpirationMinutes;
}
package com.cloudfile.cloudfile_processor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsS3Config {

    @Bean
    public S3Presigner s3Presigner(AwsProperties awsProperties) {
        return S3Presigner.builder()
                .region(Region.of(awsProperties.region()))
                .build();
    }
}

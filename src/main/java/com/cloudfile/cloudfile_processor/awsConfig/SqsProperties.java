package com.cloudfile.cloudfile_processor.awsConfig;


import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class SqsProperties {

    @Value("${sqs.queue-url}")
    private String queueUrl;
}

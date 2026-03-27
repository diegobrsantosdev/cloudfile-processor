package com.cloudfile.cloudfile_processor;

import com.cloudfile.cloudfile_processor.config.AwsProperties;
import com.cloudfile.cloudfile_processor.config.DynamoDbProperties;
import com.cloudfile.cloudfile_processor.config.S3Properties;
import com.cloudfile.cloudfile_processor.config.SqsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableConfigurationProperties({S3Properties.class, AwsProperties.class, DynamoDbProperties.class, SqsProperties.class})
@EnableScheduling
public class CloudfileProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudfileProcessorApplication.class, args);
	}

}

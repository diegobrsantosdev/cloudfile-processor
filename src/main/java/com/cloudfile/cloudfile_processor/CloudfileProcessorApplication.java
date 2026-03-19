package com.cloudfile.cloudfile_processor;

import com.cloudfile.cloudfile_processor.config.AwsProperties;
import com.cloudfile.cloudfile_processor.config.S3Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@SpringBootApplication
@EnableConfigurationProperties({S3Properties.class, AwsProperties.class})

public class CloudfileProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudfileProcessorApplication.class, args);
	}

}

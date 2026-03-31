package com.cloudfile.cloudfile_processor;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class CloudfileProcessorApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudfileProcessorApplication.class, args);
	}

}

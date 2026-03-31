package com.cloudfile.cloudfile_processor.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class DynamoDbProperties{

    @Value("${dynamodb.files-table-name}")
    private String filesTableName;

}


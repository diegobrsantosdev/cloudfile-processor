package com.cloudfile.cloudfile_processor.awsConfig;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class DynamoDbProperties{

    @Value("${dynamodb.files-table-name}")
    private String filesTableName;

    @Value("${dynamodb.file-id-index-name}")
    private String fileIdIndexName;

    @Value("${dynamodb.user-id-index-name}")
    private String userIdIndexName;
}



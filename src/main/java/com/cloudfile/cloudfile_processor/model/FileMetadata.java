package com.cloudfile.cloudfile_processor.model;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Getter
@Setter
@DynamoDbBean
public class FileMetadata {

    private String userId;
    private String fileId;

    private String fileName;
    private String mimeType;
    private Long sizeInBytes;

    private String s3Key;
    private String status;
    private String uploadDate;
    private String deletedAt;

    private String bucket;


    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    @DynamoDbSortKey
    public String getFileId() {
        return fileId;
    }
}

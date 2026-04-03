package com.cloudfile.cloudfile_processor.model;

import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

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
    @DynamoDbSecondaryPartitionKey(indexNames = "UserIdIndex")
    @DynamoDbSecondarySortKey(indexNames = "fileId-index")
    public String getUserId() {
        return userId;
    }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "fileId-index")
    public String getFileId() {
        return fileId;
    }



    //only to use enum in services
    @DynamoDbIgnore
    public UploadStatus getStatusEnum() {
        return UploadStatus.valueOf(this.status);
    }

    //only to use enum in services
    @DynamoDbIgnore
    public void setStatusEnum(UploadStatus statusEnum) {
        this.status = statusEnum.name();
    }
}

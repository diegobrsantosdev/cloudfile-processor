package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.OffsetDateTime;

@Service
public class FileDeleteService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final S3Service s3Service;
    private final S3Properties s3Properties;

    public FileDeleteService(DynamoDbTable<FileMetadata> fileTable, S3Service s3Service, S3Properties s3Properties) {
        this.fileTable = fileTable;
        this.s3Service = s3Service;
        this.s3Properties = s3Properties;
    }

    public void deleteFile(String userId, String fileId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);

        if (metadata == null || metadata.getStatus().equals(UploadStatus.DELETED.name())) {
            throw new FileNotFoundException(fileId);
        }

        String bucket = metadata.getBucket();
        if (bucket == null) {
            bucket = s3Properties.getInputBucketName();
        }

        s3Service.deleteFromS3(bucket, metadata.getS3Key());

        metadata.setStatus(UploadStatus.DELETED.name());
        metadata.setDeletedAt(OffsetDateTime.now().toString());
        fileTable.putItem(metadata);
    }
}

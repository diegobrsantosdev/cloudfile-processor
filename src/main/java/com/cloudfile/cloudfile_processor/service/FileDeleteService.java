package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileDeletionException;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.OffsetDateTime;

@Service
@AllArgsConstructor
@Slf4j
public class FileDeleteService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final FileQueryService fileQueryService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public void deleteFile(String userId, String fileId) {
        try {
            Key key = Key.builder()
                    .partitionValue(userId)
                    .sortValue(fileId)
                    .build();

            FileMetadata metadata = fileTable.getItem(key);

            validatePresenceAndStatus(metadata, fileId);

            String bucket = (metadata.getBucket() != null) ? metadata.getBucket() : s3Properties.getOutputBucketName();

            executeS3Deletion(bucket, metadata.getS3Key());

            updateStatusToDeleted(metadata);

            log.info("[DELETE-SUCCESS] User: {} | File: {} | Bucket: {}", userId, fileId, bucket);

        } catch (FileNotFoundException e) {
            throw e;
        } catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to delete file from S3. Details: {}", e.getMessage(), e);
            throw new FileDeletionException("Failed to delete file from S3", e);
        } catch (Exception e) {
            log.error("[SYSTEM-ERROR] Unexpected failure deleting File: {}", fileId, e);
            throw new FileOperationException("Internal error during deletion", e);
        }
    }

    //delete by admin (GSI)
    public void deleteFileByFileId(String fileId) {
        try {
            FileMetadata metadata = fileQueryService.getFileMetadataByFileId(fileId);

            validatePresenceAndStatus(metadata, fileId);

            executeS3Deletion(metadata.getBucket(), metadata.getS3Key());

            updateStatusToDeleted(metadata);

            log.info("[ADMIN-DELETE] File {} removed by admin from {}", fileId, metadata.getBucket());
        } catch (FileNotFoundException e) {
            throw e;
        } catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to delete file from S3. Details: {}", e.getMessage(), e);
            throw new FileDeletionException("Failed to delete file from S3", e);
        } catch (Exception e) {
            log.error("[ADMIN-ERROR] Unexpected error for File: {}", fileId, e);
            throw new FileOperationException("Could not complete admin deletion", e);
        }

    }

    private void validatePresenceAndStatus(FileMetadata metadata, String fileId) {
        if (metadata == null || UploadStatus.DELETED.name().equals(metadata.getStatus())) {
            throw new FileNotFoundException(fileId);
        }
    }

    private void executeS3Deletion(String bucket, String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(deleteRequest);
    }

    private void updateStatusToDeleted(FileMetadata metadata) {
        metadata.setStatus(UploadStatus.DELETED.name());
        metadata.setDeletedAt(OffsetDateTime.now().toString());
        fileTable.updateItem(metadata);
    }




}

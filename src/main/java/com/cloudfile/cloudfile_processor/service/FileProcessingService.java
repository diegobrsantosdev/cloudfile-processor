package com.cloudfile.cloudfile_processor.service;
import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.awsConfig.SqsProperties;
import com.cloudfile.cloudfile_processor.dto.SqsS3EventMessage;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import org.springframework.security.core.context.SecurityContextHolder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class FileProcessingService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final FileQueryService fileQueryService;
    private final S3Client s3Client;
    private final S3Properties s3Properties;
    private final SqsClient sqsClient;
    private final SqsProperties sqsProperties;
    private final ObjectMapper objectMapper;

    // ADMIN: Force reprocess
    // Only FAILED files can be reprocessed: a COMPLETED file no longer has its original
    // upload in the input bucket (the worker moves it to the output bucket).
    public void forceReprocessByFileId(String fileId) {
        FileMetadata metadata = fileQueryService.getFileMetadataByFileId(fileId);
        String adminId = SecurityContextHolder.getContext().getAuthentication().getName();

        if (UploadStatus.DELETED.name().equals(metadata.getStatus())) {
            log.warn("[AUDIT-REPROCESS-DENIED] Admin: {} tried to reprocess DELETED FileId: {}. Action blocked.",
                    adminId, fileId);
            throw new IllegalStateException("Cannot reprocess a deleted file. Physical storage is empty.");
        }

        if (!UploadStatus.FAILED.name().equals(metadata.getStatus())) {
            log.warn("[AUDIT-REPROCESS-DENIED] Admin: {} tried to reprocess FileId: {} with status {}.",
                    adminId, fileId, metadata.getStatus());
            throw new IllegalStateException(
                    "Only failed files can be reprocessed. Current status: " + metadata.getStatus());
        }

        if (!inputFileExists(metadata.getS3Key())) {
            log.warn("[AUDIT-REPROCESS-DENIED] Original upload missing for FileId: {} (key: {})",
                    fileId, metadata.getS3Key());
            throw new IllegalStateException(
                    "The original upload is no longer available. The file must be uploaded again.");
        }

        try {
            // Status first: the worker may pick the message up right after it is sent.
            metadata.setStatus(UploadStatus.PENDING.name());
            fileTable.updateItem(metadata);

            sendToProcessingQueue(metadata.getS3Key());

            log.info("[ADMIN-REPROCESS-SUCCESS] Admin: {} | FileId: {} queued for processing again",
                    adminId, fileId);

        } catch (SdkException | JsonProcessingException e) {
            log.error("[AWS-ERROR] Failed to queue reprocessing for File: {}", fileId, e);
            restoreFailedStatus(metadata);
            throw new FileOperationException("Failed to queue file for reprocessing", e);
        }
    }

    private boolean inputFileExists(String s3Key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3Properties.getInputBucketName())
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            log.error("[AWS-ERROR] Could not check input file: {}", s3Key, e);
            throw new FileOperationException("Failed to check the original upload", e);
        }
    }

    // Sends the same message shape S3 sends on upload, so the worker handles both the same way.
    private void sendToProcessingQueue(String s3Key) throws JsonProcessingException {
        SqsS3EventMessage event = new SqsS3EventMessage(List.of(
                new SqsS3EventMessage.Record(
                        new SqsS3EventMessage.S3Detail(
                                new SqsS3EventMessage.S3Object(s3Key)))));

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsProperties.getQueueUrl())
                .messageBody(objectMapper.writeValueAsString(event))
                .build());
    }

    private void restoreFailedStatus(FileMetadata metadata) {
        try {
            metadata.setStatus(UploadStatus.FAILED.name());
            fileTable.updateItem(metadata);
        } catch (SdkException e) {
            log.error("[AWS-ERROR] Could not restore FAILED status for File: {}", metadata.getFileId(), e);
        }
    }
}
package com.cloudfile.cloudfile_processor.worker;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.awsConfig.SqsProperties;
import com.cloudfile.cloudfile_processor.dto.SqsS3EventMessage;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class FileProcessingWorker {

    // Every status write is conditional: if the user deleted the file while it was
    // being processed, DynamoDB rejects the write instead of bringing the file back.
    private static final Expression NOT_DELETED = Expression.builder()
            .expression("#st <> :deleted")
            .putExpressionName("#st", "status")
            .putExpressionValue(":deleted", AttributeValue.fromS(UploadStatus.DELETED.name()))
            .build();

    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final DynamoDbTable<FileMetadata> fileTable;
    private final SqsProperties sqsProperties;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    // runs every 5 seconds
    // One message at a time: each file must finish within the queue visibility timeout.
    // With a batch of 10 large files processed sequentially, the last messages could
    // become visible again before being deleted and be processed twice.
    @Scheduled(fixedDelay = 5000)
    public void pollMessages() {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(sqsProperties.getQueueUrl())
                .maxNumberOfMessages(1)
                .waitTimeSeconds(20) // long polling
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

        for (Message message : messages) {
            try {
                processMessage(message);
                deleteMessage(message); // delete it if process successfully
            } catch (Exception ex) {
                log.error("Failed to process message: {}. Reason: {}", message.messageId(), ex.getMessage());
                markAsFailed(message);
                // it doest delete — back to queue and eventually goes to DLQ
            }
        }
    }


    //Change status to failed
    private void markAsFailed(Message message) {
        try {
            SqsS3EventMessage event = objectMapper.readValue(message.body(), SqsS3EventMessage.class);

            if (event == null || event.records() == null || event.records().isEmpty()) {
                return;
            }

            for (SqsS3EventMessage.Record record : event.records()) {
                String s3Key = record.s3().object().key();
                String[] parts = s3Key.split("/");

                if (parts.length < 3) {
                    log.error("Cannot mark as FAILED — unexpected s3Key format: {}", s3Key);
                    return;
                }

                String fileIdWithName = parts[2];

                if (fileIdWithName.length() < 36) {
                    log.error("Cannot extract fileId to mark as FAILED. fileIdWithName={}", fileIdWithName);
                    return;
                }

                String userId = parts[1];
                String fileId = fileIdWithName.substring(0, 36);

                Key key = Key.builder()
                        .partitionValue(userId)
                        .sortValue(fileId)
                        .build();

                FileMetadata metadata = fileTable.getItem(key);
                if (metadata != null) {
                    metadata.setStatus(UploadStatus.FAILED.name());
                    if (updateIfNotDeleted(metadata)) {
                        log.info("Marked fileId={} as FAILED", fileId);
                    } else {
                        log.info("fileId={} was deleted by the user, not marking as FAILED", fileId);
                    }
                } else {
                    log.warn("Could not mark as FAILED — metadata not found for fileId={}", fileId);
                }
            }
        } catch (Exception parseEx) {
            log.error("Could not mark file as FAILED — could not parse message", parseEx);
        }
    }

    private void processMessage(Message message) throws Exception {
        SqsS3EventMessage event = objectMapper.readValue(message.body(), SqsS3EventMessage.class);

        if (event == null || event.records() == null || event.records().isEmpty()) {
            log.info("Skipping message: Body is null or contains no S3 records (likely a test event).");
            return;
        }

        for (SqsS3EventMessage.Record record : event.records()) {
            String s3Key = record.s3().object().key();
            String[] parts = s3Key.split("/");
            if (parts.length < 3) {
                throw new RuntimeException("Unexpected s3Key format: " + s3Key);
            }
            String userId = parts[1];
            String fileIdWithName = parts[2];

            if (fileIdWithName.length() < 36) {
                throw new RuntimeException("Cannot extract fileId from: " + fileIdWithName);
            }

            String fileId = fileIdWithName.substring(0, 36);

            log.info("Processing file: userId={} fileId={} s3Key={}", userId, fileId, s3Key);

            // Idempotency: SQS delivers at least once, so the same message can arrive twice.
            FileMetadata current = fileTable.getItem(Key.builder()
                    .partitionValue(userId)
                    .sortValue(fileId)
                    .build());
            if (current != null) {
                String currentStatus = current.getStatus();
                if (UploadStatus.COMPLETED.name().equals(currentStatus)
                        || UploadStatus.DELETED.name().equals(currentStatus)) {
                    log.info("[WORKER] Skipping fileId={} — already {}", fileId, currentStatus);
                    continue;
                }
            }

            // update status to PROCESSING
            if (!updateStatus(userId, fileId, UploadStatus.PROCESSING)) {
                log.info("[WORKER] Skipping fileId={} — deleted before processing started", fileId);
                continue;
            }

            String outputKey = s3Key.replace("input/", "output/");

            if (objectExists(s3Properties.getInputBucketName(), s3Key)) {
                // copy from inputBucket to outputBucket
                copyToOutput(s3Key, outputKey);

                // delete from inputbucket
                deleteFromInput(s3Key);
            } else if (objectExists(s3Properties.getOutputBucketName(), outputKey)) {
                // A previous attempt copied and deleted the input but failed before
                // marking the file as COMPLETED: finish the job instead of failing it.
                log.warn("[WORKER] Input already moved for fileId={}, recovering from output", fileId);
            } else {
                throw new RuntimeException("File not found in input or output. s3Key=" + s3Key);
            }

            // updates status for COMPLETED and updates bucket
            if (!markAsCompleted(userId, fileId, outputKey)) {
                // The user deleted the file while it was being copied: honour the deletion
                // and remove the copy so no orphan object is left in the output bucket.
                log.info("[WORKER] fileId={} deleted during processing, removing output copy", fileId);
                deleteFromOutput(outputKey);
                continue;
            }

            log.info("File processed successfully: fileId={}", fileId);
        }
    }

    /** Returns false when the file was deleted in the meantime. */
    private boolean updateStatus(String userId, String fileId, UploadStatus status) {

        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);
        if (metadata == null) {
            throw new RuntimeException("FileMetadata not found for fileId: " + fileId);
        }

        metadata.setStatus(status.name());
        if (!updateIfNotDeleted(metadata)) {
            return false;
        }

        log.info("Updated status for fileId={} to {}", fileId, status);
        return true;
    }

    /** Returns false when the file was deleted in the meantime. */
    private boolean markAsCompleted(String userId, String fileId, String outputKey) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);
        if (metadata == null) {
            throw new RuntimeException("FileMetadata not found for fileId: " + fileId);
        }

        metadata.setStatus(UploadStatus.COMPLETED.name());
        metadata.setS3Key(outputKey);
        metadata.setBucket(s3Properties.getOutputBucketName());

        if (!updateIfNotDeleted(metadata)) {
            return false;
        }
        log.info("[WORKER] Success: Status COMPLETED and Bucket updated for fileId={}", fileId);
        return true;
    }

    /** Writes the item only if its current status is not DELETED. */
    private boolean updateIfNotDeleted(FileMetadata metadata) {
        try {
            fileTable.updateItem(UpdateItemEnhancedRequest.builder(FileMetadata.class)
                    .item(metadata)
                    .conditionExpression(NOT_DELETED)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException ex) {
            return false;
        }
    }

    private boolean objectExists(String bucket, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        }
    }

    private void copyToOutput(String sourceKey, String destKey) {
        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(s3Properties.getInputBucketName())
                    .sourceKey(sourceKey)
                    .destinationBucket(s3Properties.getOutputBucketName())
                    .destinationKey(destKey)
                    .build();

            s3Client.copyObject(copyRequest);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to copy file from input to output. sourceKey=" + sourceKey, ex);
        }
    }


    private void deleteFromInput(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getInputBucketName())
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to delete file from input bucket. s3Key=" + s3Key, ex);
        }
    }

    private void deleteFromOutput(String outputKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getOutputBucketName())
                    .key(outputKey)
                    .build());
        } catch (Exception ex) {
            // Not fatal: the file is already DELETED for the user, only storage is left behind.
            log.error("Failed to remove output copy of deleted file. key={}", outputKey, ex);
        }
    }

    private void deleteMessage(Message message) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(sqsProperties.getQueueUrl())
                    .receiptHandle(message.receiptHandle())
                    .build();

            sqsClient.deleteMessage(deleteRequest);
        } catch (Exception ex) {
            //logs in but does not relaunch — processing has already completed successfully
            log.error("Failed to delete SQS message: {}. It may be reprocessed.",
                    message.messageId(), ex);
        }
    }

}
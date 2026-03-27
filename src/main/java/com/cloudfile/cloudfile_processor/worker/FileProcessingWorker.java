package com.cloudfile.cloudfile_processor.worker;

import com.cloudfile.cloudfile_processor.config.S3Properties;
import com.cloudfile.cloudfile_processor.config.SqsProperties;
import com.cloudfile.cloudfile_processor.dto.SqsS3EventMessage;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class FileProcessingWorker {

    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final DynamoDbTable<FileMetadata> fileTable;
    private final SqsProperties sqsProperties;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;

    // runs every 5 seconds
    @Scheduled(fixedDelay = 5000)
    public void pollMessages() {
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20) // long polling
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

        for (Message message : messages) {
            try {
                processMessage(message);
                deleteMessage(message); // delete it if process successfully
            } catch (Exception ex) {
                log.error("Failed to process message: {}", message.messageId(), ex);
                markAsFailed(message);
                // it doest delete — back to queue and eventually goes to DLQ
            }
        }
    }

     //Change status to failed
    private void markAsFailed(Message message) {
        try {
            SqsS3EventMessage event = objectMapper.readValue(message.body(), SqsS3EventMessage.class);
            for (SqsS3EventMessage.Record record : event.records()) {
                String s3Key = record.s3().object().key();
                String[] parts = s3Key.split("/");
                String userId = parts[1];
                String fileId = parts[2].substring(0, 36);

                Key key = Key.builder()
                        .partitionValue(userId)
                        .sortValue(fileId)
                        .build();

                FileMetadata metadata = fileTable.getItem(key);
                if (metadata != null) {
                    metadata.setStatus("FAILED");
                    fileTable.putItem(metadata);
                    log.info("Marked fileId={} as FAILED", fileId);
                }
            }
        } catch (Exception parseEx) {
            log.error("Could not mark file as FAILED — could not parse message", parseEx);
        }
    }

    private void processMessage(Message message) throws Exception {
        SqsS3EventMessage event = objectMapper.readValue(message.body(), SqsS3EventMessage.class);

        for (SqsS3EventMessage.Record record : event.records()) {
            String s3Key = record.s3().object().key();

            String[] parts = s3Key.split("/");
            String userId = parts[1];
            String fileIdWithName = parts[2];

            String fileId = fileIdWithName.substring(0, 36);

            log.info("Processing file: userId={} fileId={} s3Key={}", userId, fileId, s3Key);

            // update status to PROCESSING
            updateStatus(userId, fileId, "PROCESSING", null);

            // copy from inputBucket to outputBucket
            String outputKey = s3Key.replace("input/", "output/");
            copyToOutput(s3Key, outputKey);

            // delete from inputbucket
            deleteFromInput(s3Key);

            // updates status for COMPLETED and updates bucket
            updateStatusAndBucket(userId, fileId, outputKey);

            log.info("File processed successfully: fileId={}", fileId);
        }
    }

    private void updateStatus(String userId, String fileId, String status, String outputKey) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);
        if (metadata == null) {
            throw new RuntimeException("FileMetadata not found for fileId: " + fileId);
        }

        metadata.setStatus(status);
        fileTable.putItem(metadata);
    }

    private void updateStatusAndBucket(String userId, String fileId, String outputKey) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);
        if (metadata == null) {
            throw new RuntimeException("FileMetadata not found for fileId: " + fileId);
        }

        metadata.setStatus("COMPLETED");
        metadata.setS3Key(outputKey);
        metadata.setBucket(s3Properties.outputBucketName()); // bucket updated
        fileTable.putItem(metadata);
    }

    private void copyToOutput(String sourceKey, String destKey) {
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(s3Properties.inputBucketName())
                .sourceKey(sourceKey)
                .destinationBucket(s3Properties.outputBucketName())
                .destinationKey(destKey)
                .build();

        s3Client.copyObject(copyRequest);
    }

    private void deleteFromInput(String s3Key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(s3Properties.inputBucketName())
                .key(s3Key)
                .build();

        s3Client.deleteObject(deleteRequest);
    }

    private void deleteMessage(Message message) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(sqsProperties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .build();

        sqsClient.deleteMessage(deleteRequest);
    }


}

package com.cloudfile.cloudfile_processor.worker;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.awsConfig.SqsProperties;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SQS worker. AWS clients and the DynamoDB table are mocked,
 * so these tests run without any cloud resources.
 */
@ExtendWith(MockitoExtension.class)
class FileProcessingWorkerTest {

    private static final String QUEUE_URL = "https://sqs.test/queue";
    private static final String INPUT_BUCKET = "input-bucket";
    private static final String OUTPUT_BUCKET = "output-bucket";

    private static final String USER_ID = "user-1";
    private static final String FILE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String INPUT_KEY = "input/" + USER_ID + "/" + FILE_ID + "-report.pdf";
    private static final String OUTPUT_KEY = "output/" + USER_ID + "/" + FILE_ID + "-report.pdf";

    @Mock private SqsClient sqsClient;
    @Mock private S3Client s3Client;
    @Mock private DynamoDbTable<FileMetadata> fileTable;
    @Mock private SqsProperties sqsProperties;
    @Mock private S3Properties s3Properties;

    private FileProcessingWorker worker;
    private FileMetadata metadata;

    @BeforeEach
    void setUp() {
        worker = new FileProcessingWorker(
                sqsClient, s3Client, fileTable, sqsProperties, s3Properties, new ObjectMapper());

        lenient().when(sqsProperties.getQueueUrl()).thenReturn(QUEUE_URL);
        lenient().when(s3Properties.getInputBucketName()).thenReturn(INPUT_BUCKET);
        lenient().when(s3Properties.getOutputBucketName()).thenReturn(OUTPUT_BUCKET);

        metadata = new FileMetadata();
        metadata.setUserId(USER_ID);
        metadata.setFileId(FILE_ID);
        metadata.setS3Key(INPUT_KEY);
        metadata.setBucket(INPUT_BUCKET);
        metadata.setStatus(UploadStatus.PENDING.name());

        // One S3 event for the test file waiting in the queue
        Message message = Message.builder()
                .messageId("msg-1")
                .receiptHandle("receipt-1")
                .body("{\"Records\":[{\"s3\":{\"object\":{\"key\":\"" + INPUT_KEY + "\"}}}]}")
                .build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        lenient().when(fileTable.getItem(any(Key.class))).thenReturn(metadata);
    }

    @Test
    @DisplayName("Moves the file from input to output and marks it as COMPLETED")
    void shouldProcessFileAndMarkAsCompleted() {
        worker.pollMessages();

        verify(s3Client).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest r) ->
                r.bucket().equals(INPUT_BUCKET) && r.key().equals(INPUT_KEY)));

        assertThat(metadata.getStatus()).isEqualTo(UploadStatus.COMPLETED.name());
        assertThat(metadata.getS3Key()).isEqualTo(OUTPUT_KEY);
        assertThat(metadata.getBucket()).isEqualTo(OUTPUT_BUCKET);

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Skips a duplicate message for a file that is already COMPLETED")
    void shouldSkipAlreadyCompletedFile() {
        metadata.setStatus(UploadStatus.COMPLETED.name());

        worker.pollMessages();

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(fileTable, never()).updateItem(any(UpdateItemEnhancedRequest.class));
        // The duplicate is acknowledged so it does not come back
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Skips a message for a file the user already deleted")
    void shouldSkipDeletedFile() {
        metadata.setStatus(UploadStatus.DELETED.name());

        worker.pollMessages();

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(fileTable, never()).updateItem(any(UpdateItemEnhancedRequest.class));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Finishes the job when a previous attempt already moved the file to output")
    void shouldRecoverWhenInputWasAlreadyMovedToOutput() {
        // Input is gone, output exists
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (INPUT_BUCKET.equals(request.bucket())) {
                throw NoSuchKeyException.builder().build();
            }
            return HeadObjectResponse.builder().build();
        });

        worker.pollMessages();

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(metadata.getStatus()).isEqualTo(UploadStatus.COMPLETED.name());
        assertThat(metadata.getS3Key()).isEqualTo(OUTPUT_KEY);
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Marks the file as FAILED and keeps the message for retry when the file is missing")
    void shouldMarkAsFailedWhenFileIsMissingEverywhere() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        worker.pollMessages();

        assertThat(metadata.getStatus()).isEqualTo(UploadStatus.FAILED.name());
        // Not deleted: SQS retries it and, after the max attempts, moves it to the DLQ
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Does nothing when the file is deleted right before processing starts")
    void shouldSkipWhenFileIsDeletedBeforeProcessingStarts() {
        when(fileTable.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("deleted").build());

        worker.pollMessages();

        verify(s3Client, never()).copyObject(any(CopyObjectRequest.class));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("Does not bring back a file deleted during processing and removes its output copy")
    void shouldNotOverwriteFileDeletedDuringProcessing() {
        // 1st write (PROCESSING) succeeds, 2nd write (COMPLETED) is rejected: the user deleted it meanwhile
        when(fileTable.updateItem(any(UpdateItemEnhancedRequest.class)))
                .thenReturn(metadata)
                .thenThrow(ConditionalCheckFailedException.builder().message("deleted").build());

        worker.pollMessages();

        verify(fileTable, times(2)).updateItem(any(UpdateItemEnhancedRequest.class));
        verify(s3Client).deleteObject(argThat((DeleteObjectRequest r) ->
                r.bucket().equals(OUTPUT_BUCKET) && r.key().equals(OUTPUT_KEY)));
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }
}
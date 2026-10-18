package com.cloudfile.cloudfile_processor.worker;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.awsConfig.SqsProperties;
import com.cloudfile.cloudfile_processor.dto.SqsS3EventMessage;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.cloudfile.cloudfile_processor.service.FileProcessingService;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the admin reprocess rules.
 */
@ExtendWith(MockitoExtension.class)
class FileProcessingServiceTest {

    private static final String FILE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String INPUT_KEY = "input/user-1/" + FILE_ID + "-report.pdf";
    private static final String QUEUE_URL = "https://sqs.test/queue";

    @Mock private DynamoDbTable<FileMetadata> fileTable;
    @Mock private FileQueryService fileQueryService;
    @Mock private S3Client s3Client;
    @Mock private S3Properties s3Properties;
    @Mock private SqsClient sqsClient;
    @Mock private SqsProperties sqsProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FileProcessingService service;
    private FileMetadata metadata;

    @BeforeEach
    void setUp() {
        service = new FileProcessingService(
                fileTable, fileQueryService, s3Client, s3Properties, sqsClient, sqsProperties, objectMapper);

        lenient().when(s3Properties.getInputBucketName()).thenReturn("input-bucket");
        lenient().when(sqsProperties.getQueueUrl()).thenReturn(QUEUE_URL);

        metadata = new FileMetadata();
        metadata.setUserId("user-1");
        metadata.setFileId(FILE_ID);
        metadata.setS3Key(INPUT_KEY);
        when(fileQueryService.getFileMetadataByFileId(FILE_ID)).thenReturn(metadata);

        // The service logs which admin triggered the action
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin-1", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Resets a FAILED file to PENDING and sends it back to the queue")
    void shouldQueueFailedFileForReprocessing() throws Exception {
        metadata.setStatus(UploadStatus.FAILED.name());

        service.forceReprocessByFileId(FILE_ID);

        assertThat(metadata.getStatus()).isEqualTo(UploadStatus.PENDING.name());

        // Status is saved before the message is sent, so the worker never sees a stale status
        InOrder order = inOrder(fileTable, sqsClient);
        order.verify(fileTable).updateItem(any(FileMetadata.class));
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        order.verify(sqsClient).sendMessage(captor.capture());

        // The message has the same shape as an S3 event, pointing to the original upload
        SendMessageRequest sent = captor.getValue();
        assertThat(sent.queueUrl()).isEqualTo(QUEUE_URL);
        SqsS3EventMessage event = objectMapper.readValue(sent.messageBody(), SqsS3EventMessage.class);
        assertThat(event.records()).hasSize(1);
        assertThat(event.records().get(0).s3().object().key()).isEqualTo(INPUT_KEY);
    }

    @Test
    @DisplayName("Rejects reprocessing a COMPLETED file")
    void shouldRejectCompletedFile() {
        metadata.setStatus(UploadStatus.COMPLETED.name());

        assertThatThrownBy(() -> service.forceReprocessByFileId(FILE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only failed files can be reprocessed");

        verify(fileTable, never()).updateItem(any(FileMetadata.class));
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Rejects reprocessing a DELETED file")
    void shouldRejectDeletedFile() {
        metadata.setStatus(UploadStatus.DELETED.name());

        assertThatThrownBy(() -> service.forceReprocessByFileId(FILE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deleted");

        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Rejects reprocessing when the original upload is no longer in the input bucket")
    void shouldRejectWhenOriginalUploadIsMissing() {
        metadata.setStatus(UploadStatus.FAILED.name());
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> service.forceReprocessByFileId(FILE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer available");

        verify(fileTable, never()).updateItem(any(FileMetadata.class));
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("Restores the FAILED status when the queue is unavailable")
    void shouldRestoreFailedStatusWhenQueueIsUnavailable() {
        metadata.setStatus(UploadStatus.FAILED.name());
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("queue unavailable").build());

        assertThatThrownBy(() -> service.forceReprocessByFileId(FILE_ID))
                .isInstanceOf(FileOperationException.class);

        // 1st write: PENDING, 2nd write: back to FAILED
        verify(fileTable, times(2)).updateItem(any(FileMetadata.class));
        assertThat(metadata.getStatus()).isEqualTo(UploadStatus.FAILED.name());
    }
}
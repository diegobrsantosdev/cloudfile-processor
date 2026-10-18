package com.cloudfile.cloudfile_processor.worker;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.cloudfile.cloudfile_processor.service.S3PresignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the download rules.
 */
@ExtendWith(MockitoExtension.class)
class FileQueryServiceTest {

    private static final String USER_ID = "user-1";
    private static final String FILE_ID = "11111111-2222-3333-4444-555555555555";
    private static final String OUTPUT_KEY = "output/" + USER_ID + "/" + FILE_ID + "-report.pdf";

    @Mock private DynamoDbTable<FileMetadata> fileTable;
    @Mock private S3PresignedUrlService s3PresignedUrlService;
    @Mock private DynamoDbIndex<FileMetadata> fileIdIndex;
    @Mock private DynamoDbIndex<FileMetadata> userIdIndex;

    private FileQueryService service;
    private FileMetadata metadata;

    @BeforeEach
    void setUp() {
        service = new FileQueryService(fileTable, s3PresignedUrlService, fileIdIndex, userIdIndex);

        metadata = new FileMetadata();
        metadata.setUserId(USER_ID);
        metadata.setFileId(FILE_ID);
        metadata.setFileName("report.pdf");
        metadata.setS3Key(OUTPUT_KEY);
    }

    @Test
    @DisplayName("Returns a download link for a COMPLETED file")
    void shouldReturnDownloadUrlForCompletedFile() {
        metadata.setStatus(UploadStatus.COMPLETED.name());
        when(fileTable.getItem(any(Key.class))).thenReturn(metadata);
        when(s3PresignedUrlService.generatePresignedDownloadUrl(OUTPUT_KEY)).thenReturn("https://signed-url");

        FileDownloadResponse response = service.getDownloadUrl(USER_ID, FILE_ID);

        assertThat(response.fileId()).isEqualTo(FILE_ID);
        assertThat(response.fileName()).isEqualTo("report.pdf");
        assertThat(response.preSignedUrl()).isEqualTo("https://signed-url");
    }

    @Test
    @DisplayName("Rejects the download while the file is not processed yet")
    void shouldRejectDownloadWhenFileIsNotCompleted() {
        metadata.setStatus(UploadStatus.PENDING.name());
        when(fileTable.getItem(any(Key.class))).thenReturn(metadata);

        assertThatThrownBy(() -> service.getDownloadUrl(USER_ID, FILE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready for download");

        // No link is generated for an object that does not exist yet
        verify(s3PresignedUrlService, never()).generatePresignedDownloadUrl(anyString());
    }

    @Test
    @DisplayName("Returns not found for a DELETED file")
    void shouldReturnNotFoundForDeletedFile() {
        metadata.setStatus(UploadStatus.DELETED.name());
        when(fileTable.getItem(any(Key.class))).thenReturn(metadata);

        assertThatThrownBy(() -> service.getDownloadUrl(USER_ID, FILE_ID))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Returns not found when the file does not exist for this user")
    void shouldReturnNotFoundWhenFileDoesNotExist() {
        when(fileTable.getItem(any(Key.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getDownloadUrl(USER_ID, FILE_ID))
                .isInstanceOf(FileNotFoundException.class);
    }
}

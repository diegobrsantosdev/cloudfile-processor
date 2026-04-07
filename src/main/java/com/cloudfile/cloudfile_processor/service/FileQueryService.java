package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
@Slf4j
@Service
@AllArgsConstructor
public class FileQueryService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final DynamoDbIndex<FileMetadata> fileIdIndex;
    private final DynamoDbIndex<FileMetadata> userIdIndex;

    public List<FileListResponse> listActiveFiles(String userId) {
        return listFiles(userId, false);
    }

    public List<FileListResponse> listAllFiles(String userId) {
        return listFiles(userId, true);
    }

    //using in listallfiles and listactivefiles (for users)
    private List<FileListResponse> listFiles(String userId, boolean includeAllStatus) {
        log.info("[QUERY] Listing files for user: {} (includeAllStatus: {})", userId, includeAllStatus);

        try {
            QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()));

            if (!includeAllStatus) {
                requestBuilder.filterExpression(Expression.builder()
                        .expression("#st IN (:comp, :up, :proc)")
                        .putExpressionName("#st", "status")
                        .putExpressionValue(":comp", AttributeValue.fromS(UploadStatus.COMPLETED.name()))
                        .putExpressionValue(":up", AttributeValue.fromS(UploadStatus.UPLOADED.name()))
                        .putExpressionValue(":proc", AttributeValue.fromS(UploadStatus.PROCESSING.name()))
                        .build());
            }

            return fileTable.query(requestBuilder.build())
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .map(this::toFileListResponse)
                    .toList();

        } catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to query files for user: {}", userId, e);
            throw new FileOperationException("Failed to retrieve file list", e);
        }
    }

    //Download file endpoint
    public FileDownloadResponse getDownloadUrl(String userId, String fileId) {
        log.info("[DOWNLOAD] Requesting download URL for fileId: {} (UserId: {})", fileId, userId);

        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(fileId)
                .build();

        FileMetadata metadata = fileTable.getItem(key);

        if (metadata == null || metadata.getStatusEnum() == UploadStatus.DELETED) {
            throw new FileNotFoundException(fileId);
        }

        String preSignedUrl = s3PresignedUrlService.generatePresignedDownloadUrl(metadata.getS3Key());

        return new FileDownloadResponse(
                metadata.getFileId(),
                metadata.getFileName(),
                preSignedUrl
        );
    }

    //ADMIN: list all users with files (GSI Scan)
    public List<String> listAllUsers() {
        try {
            log.warn("[ADMIN-OPERATION] Scanning UserID-Index with attribute projection.");

            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                    .attributesToProject("userId")
                    .build();

            return userIdIndex.scan(scanRequest)
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .map(FileMetadata::getUserId)
                    .distinct()
                    .toList();

        } catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to scan user index. Details: {}", e.getMessage(), e);
            throw new FileOperationException("Could not retrieve user list from infrastructure", e);
        }
    }

    //ADMIN: used to get file metadata by fileId (GSI)
    public FileMetadata getFileMetadataByFileId(String fileId) {
        log.info("[ADMIN-QUERY] Searching metadata by FileId: {}", fileId);

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(fileId).build()))
                .limit(1)
                .build();

        return fileIdIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .orElseThrow(() -> {
                    log.error("[ADMIN-QUERY-FAILED] Metadata not found for fileId: {}", fileId);
                    return new FileNotFoundException(fileId);
                });
    }

    private FileListResponse toFileListResponse(FileMetadata item) {
        return new FileListResponse(
                item.getFileId(),
                item.getS3Key(),
                item.getFileName(),
                item.getMimeType(),
                item.getSizeInBytes(),
                item.getUploadDate(),
                item.getStatusEnum().name()
        );
    }

}

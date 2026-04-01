package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Service
public class FileQueryService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final S3PresignedUrlService s3PresignedUrlService;

    public FileQueryService( DynamoDbTable<FileMetadata> fileTable, S3PresignedUrlService s3PresignedUrlService) {
        this.fileTable = fileTable;
        this.s3PresignedUrlService = s3PresignedUrlService;
    }

    //used to get uploaded files by user and user history
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

    private List<FileListResponse> listFiles(String userId, boolean includeDeleted) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(userId).build()
                ))
                .build();

        return fileTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> includeDeleted || item.getStatusEnum() == UploadStatus.COMPLETED)
                .map(this::toFileListResponse)
                .toList();
    }

    public List<FileListResponse> listActiveFiles(String userId) {
        return listFiles(userId, false);
    }

    public List<FileListResponse> listAllFiles(String userId) {
        return listFiles(userId, true);
    }


    //Download file endpoint
    public FileDownloadResponse getDownloadUrl(String userId, String fileId) {
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

}

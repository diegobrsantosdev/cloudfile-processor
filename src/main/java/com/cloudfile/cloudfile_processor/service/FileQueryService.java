package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.cloudfile.cloudfile_processor.repository.FileMetadataRepository;
import com.cloudfile.cloudfile_processor.security.UserContext;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Service
public class FileQueryService {

    private final DynamoDbTable<FileMetadata> fileTable;

    public FileQueryService( DynamoDbTable<FileMetadata> fileTable) {
        this.fileTable = fileTable;

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
                item.getStatus()
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
                .filter(item -> includeDeleted || "UPLOADED".equals(item.getStatus()))
                .map(this::toFileListResponse)
                .toList();
    }

    public List<FileListResponse> listActiveFiles(String userId) {
        return listFiles(userId, false);
    }

    public List<FileListResponse> listAllFiles(String userId) {
        return listFiles(userId, true);
    }



}

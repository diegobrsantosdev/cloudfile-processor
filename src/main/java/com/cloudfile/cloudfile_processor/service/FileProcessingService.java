package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Service
@AllArgsConstructor
public class FileProcessingService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final FileQueryService fileQueryService;

    // ADMIN: Force reprocess
    public void forceReprocessByFileId(String fileId) {

        FileMetadata metadata = fileQueryService.getFileMetadataByFileId(fileId);

        metadata.setStatus(UploadStatus.PENDING.name());

        fileTable.updateItem(metadata);
    }
}

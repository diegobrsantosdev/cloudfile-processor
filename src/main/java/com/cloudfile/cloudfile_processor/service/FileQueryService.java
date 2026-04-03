package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
@AllArgsConstructor
@Service
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

    //using in listallfiles and listactivefiles (user only)
    private List<FileListResponse> listFiles(String userId, boolean includeAllStatus) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .build();

        return fileTable.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> {
                    if (includeAllStatus) return true; //return all files

                    //return only files with those statuses
                    return item.getStatusEnum() == UploadStatus.COMPLETED ||
                            item.getStatusEnum() == UploadStatus.UPLOADED ||
                            item.getStatusEnum() == UploadStatus.PROCESSING;
                })
                .map(this::toFileListResponse)
                .toList();
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


    //ADMIN: list all users with files (GSI Scam)
    public List<String> listAllUsers() {
        return userIdIndex.scan()
                .stream()
                .flatMap(page -> page.items().stream())
                .map(FileMetadata::getUserId)
                .distinct()
                .toList();
    }


    //ADMIN: used to get file metadata by fileId (GSI)
    public FileMetadata getFileMetadataByFileId(String fileId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(fileId).build()))
                .limit(1)
                .build();

        return fileIdIndex.query(request)
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException(fileId));
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

package com.cloudfile.cloudfile_processor.service;
import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.dto.FileUploadRequest;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileUploadProcessingException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.cloudfile.cloudfile_processor.repository.FileMetadataRepository;
import com.cloudfile.cloudfile_processor.security.UserContext;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.OffsetDateTime;
import java.util.UUID;


@Service
public class FileUploadService {

    private final UserContext userContext;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final FileMetadataRepository fileMetaDataRepository;
    private final DynamoDbTable<FileMetadata> fileTable;
    private final S3Properties s3Properties;
    private static final int URL_EXPIRATION_MINUTES = 15;

    public FileUploadService(
            S3PresignedUrlService s3PresignedUrlService,
            UserContext userContext,
            FileMetadataRepository fileMetaDataRepository,
            DynamoDbTable<FileMetadata> fileTable,
            S3Properties s3Properties)
    {
        this.s3PresignedUrlService = s3PresignedUrlService;
        this.userContext = userContext;
        this.fileMetaDataRepository = fileMetaDataRepository;
        this.fileTable = fileTable;
        this.s3Properties = s3Properties;
    }

    //Used to send files
    public FileUploadResponse createUploadRequest(FileUploadRequest request) {
        String userId = userContext.getUserId();
        String uploadId = generateUploadId();
        String s3Key = buildS3Key(
                userId,
                uploadId,
                request.originalFileName());

        String preSignedUrl;
        try {
            preSignedUrl = s3PresignedUrlService.generatePresignedUploadUrl(s3Key);
        } catch (Exception ex) {
            throw new FileUploadProcessingException("Failed to generate upload URL", ex);
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(URL_EXPIRATION_MINUTES);

        FileMetadata metadata = new FileMetadata();
        metadata.setUserId(userId);
        metadata.setFileId(uploadId);
        metadata.setFileName(request.originalFileName());
        metadata.setMimeType(request.mimeType());
        metadata.setSizeInBytes(request.sizeInBytes());
        metadata.setS3Key(s3Key);
        metadata.setBucket(s3Properties.getInputBucketName());
        metadata.setStatus(UploadStatus.PENDING.name());
        metadata.setUploadDate(OffsetDateTime.now().toString());

        try {
            fileMetaDataRepository.save(metadata);
        } catch (Exception ex) {
            throw new FileUploadProcessingException("Failed to save file metadata");
        }

        return new FileUploadResponse(
                uploadId,
                s3Key,
                preSignedUrl,
                expiresAt,
                UploadStatus.PENDING
        );
    }

    private String generateUploadId() {
        return UUID.randomUUID().toString();
    }

    private String buildS3Key(String userId, String uploadId, String originalFileName) {
        return "input/" + userId + "/" + uploadId + "-" + sanitizeFileName(originalFileName);
    }

    private String sanitizeFileName(String fileName) {
        return fileName
                .trim()
                .replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
    }

}

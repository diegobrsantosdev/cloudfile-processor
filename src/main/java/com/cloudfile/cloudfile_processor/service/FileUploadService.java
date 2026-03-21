package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileUploadRequest;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.security.UserContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;


@Service
public class FileUploadService {

    private final UserContext userContext;
    private final S3PresignedUrlService s3PresignedUrlService;
    private static final int URL_EXPIRATION_MINUTES = 15;

    public FileUploadService(S3PresignedUrlService s3PresignedUrlService, UserContext userContext) {
        this.s3PresignedUrlService = s3PresignedUrlService;
        this.userContext = userContext;
    }

    public FileUploadResponse createUploadRequest(FileUploadRequest request) {
        String userId = userContext.getUserId();
        String uploadId = generateUploadId();
        String s3Key = buildS3Key(
                userId,
                uploadId,
                request.originalFileName());


        String preSignedUrl = s3PresignedUrlService.generatePresignedUploadUrl(s3Key);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(URL_EXPIRATION_MINUTES);


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
        return fileName.trim().replaceAll("\\s+", "_");
    }

}

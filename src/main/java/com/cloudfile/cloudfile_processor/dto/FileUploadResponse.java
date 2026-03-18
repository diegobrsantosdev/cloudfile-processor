package com.cloudfile.cloudfile_processor.dto;

import com.cloudfile.cloudfile_processor.enums.UploadStatus;

import java.time.OffsetDateTime;

public record FileUploadResponse(
        String uploadId,
        String s3key,
        String preSignedUrl,
        OffsetDateTime expiresAt,
        UploadStatus status
) {
}

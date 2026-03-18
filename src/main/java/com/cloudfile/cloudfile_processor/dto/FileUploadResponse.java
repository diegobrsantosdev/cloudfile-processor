package com.cloudfile.cloudfile_processor.dto;

import java.time.OffsetDateTime;

public record FileUploadResponse(
        String uploadId,
        String s3key,
        String preSignedUrl,
        OffsetDateTime expiresAt,
        String status
) {
}

package com.cloudfile.cloudfile_processor.dto;

import com.cloudfile.cloudfile_processor.enums.UploadStatus;

import java.time.OffsetDateTime;

public record FileListResponse(
        String uploadId,
        String s3Key,
        String fileName,
        String fileType,
        Long fileSize,
        String uploadDate,
        String status
) {
}

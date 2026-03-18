package com.cloudfile.cloudfile_processor.dto;

public record FileUploadRequest(
        String userId,
        String originalFineName,
        String mimeType,
        Long sizeInBytes
) {
}

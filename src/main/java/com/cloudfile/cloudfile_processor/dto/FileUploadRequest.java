package com.cloudfile.cloudfile_processor.dto;

public record FileUploadRequest(

        String userId,
        String originalFileName,
        String mimeType,
        Long sizeInBytes
) {
}

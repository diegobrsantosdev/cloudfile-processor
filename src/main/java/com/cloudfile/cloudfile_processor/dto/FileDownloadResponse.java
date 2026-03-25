package com.cloudfile.cloudfile_processor.dto;

public record FileDownloadResponse(
        String fileId,
        String fileName,
        String preSignedUrl
) {
}

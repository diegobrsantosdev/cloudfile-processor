package com.cloudfile.cloudfile_processor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FileUploadRequest(

        @NotBlank( message = "UserId is required")
        String userId,

        @NotBlank( message = "OriginalFileName is required")
        String originalFileName,

        @NotBlank( message = "MimeType is required")
        String mimeType,

        @NotNull
        @Positive(message = "sizeInBytes must be greater than 0")
        Long sizeInBytes

) {
}

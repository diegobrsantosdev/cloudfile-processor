package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.dto.FileUploadRequest;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileUploadProcessingException;
import com.cloudfile.cloudfile_processor.exceptions.InvalidFileUploadRequestException;

import java.time.OffsetDateTime;
import java.util.UUID;



public class FileUploadService {

    private static final int URL_EXPIRATION_MINUTES = 15;

    public FileUploadResponse createUploadRequest(FileUploadRequest request){

        String uploadId = generateUploadId();
        String s3Key = buildS3Key(request.userId(), uploadId, request.originalFileName());
        String preSignedUrl = generateFakePreSignedUrl(s3Key);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(URL_EXPIRATION_MINUTES);

        return new FileUploadResponse(uploadId, s3Key, preSignedUrl, expiresAt, UploadStatus.UPLOADED
        );

    }

//    private void validateRequest(FileUploadRequest request) {
//        if (request == null){
//            throw new InvalidFileUploadRequestException("Upload request cant be null");
//        }
//        if (isBlank(request.userId())){
//            throw new InvalidFileUploadRequestException("UserId is required");
//        }
//        if (isBlank(request.originalFileName())){
//            throw new InvalidFileUploadRequestException("OriginalFile name is required");
//        }
//        if (isBlank(request.mimeType())){
//            throw new InvalidFileUploadRequestException("MimeType is required");
//        }
//        if (request.sizeInBytes() == null || request.sizeInBytes() <= 0) {
//            throw new InvalidFileUploadRequestException("sizeInBytes must be greater than 0.");
//        }
//    }

    private String generateUploadId() {
        return UUID.randomUUID().toString();
    }
    private String buildS3Key(String userId, String uploadId, String originalFileName) {
        return "input/" + userId + "/" + uploadId + "-" + sanitizeFileName(originalFileName);
    }

    private String sanitizeFileName(String fileName) {
        return fileName.trim().replaceAll("\\s+", "_");
    }

    private String generateFakePreSignedUrl(String s3Key) {
        try {
            return "https://fake-s3-presigned-url.local/upload?key=" + s3Key + "&signature=mock-signature";
        } catch (Exception ex) {
            throw new FileUploadProcessingException("Failed to generate temporary URL.", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

}

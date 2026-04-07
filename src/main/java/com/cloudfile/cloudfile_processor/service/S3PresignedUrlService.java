package com.cloudfile.cloudfile_processor.service;
import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.exceptions.FileUploadProcessingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
@Slf4j
@Service
@AllArgsConstructor
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;


    public String generatePresignedUploadUrl(String s3Key) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.getInputBucketName())
                    .key(s3Key)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes()))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
            log.info("[S3] Presigned UPLOAD URL generated for key: {}", s3Key);
            return presignedRequest.url().toString();

        } catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to presign UPLOAD for key: {}. Details: {}", s3Key, e.getMessage(), e);
            throw new FileOperationException("Failed to prepare secure upload link", e);
        }
    }


    public String generatePresignedDownloadUrl(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.getOutputBucketName())
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes()))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            log.info("[S3] Presigned DOWNLOAD URL generated for key: {}", s3Key);
            return presignedRequest.url().toString();

        }  catch (SdkException e) {
            log.error("[AWS-ERROR] Failed to presign DOWNLOAD for key: {}. Details: {}", s3Key, e.getMessage(), e);
            throw new FileOperationException("Failed to prepare secure download link", e);
        }
    }


}

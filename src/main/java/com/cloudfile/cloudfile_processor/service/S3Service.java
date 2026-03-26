package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.config.S3Properties;
import com.cloudfile.cloudfile_processor.exceptions.FileDeletionException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class S3Service {

    private final S3Client s3Client;

    public S3Service(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
    }

    public void deleteFromS3(String bucket, String s3Key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        try {
            s3Client.deleteObject(deleteRequest);
        } catch (Exception ex) {
            throw new FileDeletionException("Failed to delete file from S3", ex);
        }
    }
}

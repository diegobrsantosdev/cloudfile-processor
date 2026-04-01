package com.cloudfile.cloudfile_processor.service;

import com.cloudfile.cloudfile_processor.awsConfig.S3Properties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public S3PresignedUrlService(S3Presigner s3Presigner, S3Properties s3Properties) {
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
    }

    //Used to generatePreSignedUrl
    public String generatePresignedUploadUrl(String s3Key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getInputBucketName())
                .key(s3Key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return presignedRequest.url().toString();
    }

    //Used to download files using PreSignedUrl
    public String generatePresignedDownloadUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.getInputBucketName())
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }


}

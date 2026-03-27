package com.cloudfile.cloudfile_processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SqsS3EventMessage(
        @JsonProperty("Records") List<Record> records
) {
    public record Record(
            @JsonProperty("s3") S3Detail s3
    ) {}

    public record S3Detail(
            @JsonProperty("object") S3Object object
    ) {}

    public record S3Object(
            @JsonProperty("key") String key
    ) {}
}

package com.cloudfile.cloudfile_processor.repository;

import com.cloudfile.cloudfile_processor.model.FileMetadata;

import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@Repository
public class FileMetadataRepository {

    private final DynamoDbTable<FileMetadata> table;

    public FileMetadataRepository(DynamoDbTable<FileMetadata> table) {
        this.table = table;
    }

    public void save(FileMetadata metadata) {
        table.putItem(metadata);
    }

}

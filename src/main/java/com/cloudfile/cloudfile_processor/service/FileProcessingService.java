package com.cloudfile.cloudfile_processor.service;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@AllArgsConstructor
@Slf4j
public class FileProcessingService {

    private final DynamoDbTable<FileMetadata> fileTable;
    private final FileQueryService fileQueryService;

    // ADMIN: Force reprocess
    public void forceReprocessByFileId(String fileId) {
        try{
            FileMetadata metadata = fileQueryService.getFileMetadataByFileId(fileId);
            String adminId = SecurityContextHolder.getContext().getAuthentication().getName();

            if (UploadStatus.DELETED.name().equals(metadata.getStatus())) {
                log.warn("[AUDIT-REPROCESS-DENIED] Admin: {} tried to reprocess DELETED FileId: {}. Action blocked.",
                        adminId, fileId);
                throw new IllegalStateException("Cannot reprocess a deleted file. Physical storage is empty.");
            }

            metadata.setStatus(UploadStatus.PENDING.name());

            fileTable.updateItem(metadata);

            log.info("[ADMIN-REPROCESS-SUCCESS] FileId: {} status reset to PENDING", fileId);


        } catch (SdkException e) {
            log.error("[AWS-ERROR] DynamoDB failed to update status for File: {}", fileId, e);
            throw new FileOperationException("Failed to update file status", e);

        } catch (Exception e) {
            log.error("[ADMIN-ERROR] Failure for FileId: {}", fileId, e);
            throw e;
        }

    }
}

package com.cloudfile.cloudfile_processor.controller;

import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.service.FileDeleteService;
import com.cloudfile.cloudfile_processor.service.FileProcessingService;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/files")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class AdminFileController {

    private final FileQueryService fileQueryService;
    private final FileDeleteService fileDeleteService;
    private final FileProcessingService fileProcessingService;


    @GetMapping("/users")
    public ResponseEntity<List<String>> listAllUsers() {
        return ResponseEntity.ok(fileQueryService.listAllUsers());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<List<FileListResponse>> listFilesByUser(@PathVariable String userId) {
        return ResponseEntity.ok(fileQueryService.listAllFiles(userId));
    }

    @PostMapping("/{fileId}/reprocess")
    public ResponseEntity<Void> reprocessFile(@PathVariable String fileId) {
        fileProcessingService.forceReprocessByFileId(fileId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        fileDeleteService.deleteFileByFileId(fileId);
        return ResponseEntity.noContent().build();
    }

}

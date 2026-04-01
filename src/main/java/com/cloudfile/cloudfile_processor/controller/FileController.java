package com.cloudfile.cloudfile_processor.controller;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.dto.FileUploadRequest;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.security.UserContext;
import com.cloudfile.cloudfile_processor.service.FileDeleteService;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.cloudfile.cloudfile_processor.service.FileUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileQueryService fileQueryService;
    private final FileDeleteService fileDeleteService;
    private final UserContext userContext;


    @PostMapping
    public ResponseEntity<FileUploadResponse> createUploadRequest(
            @Valid @RequestBody FileUploadRequest request
    ){ return ResponseEntity.ok(fileUploadService.createUploadRequest(request));}


    @GetMapping
    public ResponseEntity<List<FileListResponse>> listFiles() {
        String userId = userContext.getUserId();
        return ResponseEntity.ok(fileQueryService.listActiveFiles(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<FileListResponse>> listHistory() {
        String userId = userContext.getUserId();
        return ResponseEntity.ok(fileQueryService.listAllFiles(userId));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<FileDownloadResponse> downloadFile(@PathVariable String fileId) {
        String userId = userContext.getUserId();
        return ResponseEntity.ok(fileQueryService.getDownloadUrl(userId, fileId));
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable @NotBlank String fileId) {
        String userId = userContext.getUserId();
        fileDeleteService.deleteFile(userId, fileId);
        return ResponseEntity.noContent().build();
    }
}

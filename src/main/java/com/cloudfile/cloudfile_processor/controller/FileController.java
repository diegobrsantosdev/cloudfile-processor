package com.cloudfile.cloudfile_processor.controller;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.dto.FileUploadRequest;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.security.UserContext;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.cloudfile.cloudfile_processor.service.FileUploadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileQueryService fileQueryService;
    private final UserContext userContext;


    public FileController(FileUploadService fileUploadService, FileQueryService fileQueryService, UserContext userContext) {
        this.fileUploadService = fileUploadService;
        this.fileQueryService = fileQueryService;
        this.userContext = userContext;
    }

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
}

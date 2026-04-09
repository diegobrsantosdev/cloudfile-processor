package com.cloudfile.cloudfile_processor.controller;

import com.cloudfile.cloudfile_processor.dto.FileDownloadResponse;
import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.dto.FileUploadResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.security.UserContext;
import com.cloudfile.cloudfile_processor.service.FileDeleteService;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.cloudfile.cloudfile_processor.service.FileUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(FileController.class)
class FileControllerTest {

    public static final String UPLOAD_REQUEST = "/api/v1/files";
    public static final String LIST_FILES = "/api/v1/files";
    public static final String HISTORY = "/api/v1/files/history";
    public static final String DOWNLOAD_FILES = "/api/v1/files/{fileId}";
    public static final String DELETE_FILES = "/api/v1/files/{fileId}";


    @Autowired
    private MockMvc mockMvc;

    @MockBean
    FileUploadService fileUploadService;

    @MockBean
    FileQueryService fileQueryService;

    @MockBean
    FileDeleteService fileDeleteService;

    @MockBean
    UserContext userContext;

    @DisplayName( "Should be successful when upload is success")
    @Test
    public void shouldBe200WhenUploadIsSuccess() throws Exception {
        var now = OffsetDateTime.now();
        var response = new FileUploadResponse(
                "id",
                "s3key",
                "preSignedUrl",
                now,
                UploadStatus.PENDING
        );
        when(fileUploadService.createUploadRequest(any())).thenReturn(response);
        var uploadRequest = """
                  {"originalFileName":"test.txt","mimeType":"text/plain","sizeInBytes":10}
                """;
        this.mockMvc.perform(post(UPLOAD_REQUEST)
                        .contentType("application/json")
                .content(uploadRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value("id"))
                .andExpect(jsonPath("$.s3Key").value("s3key"))
                .andExpect(jsonPath("$.preSignedUrl").value("preSignedUrl"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @DisplayName("Should be successful when listing files")
    @Test
    public void shouldBe200WhenListingFiles() throws Exception {
        var response = new FileListResponse(
                "id",
                "s3key",
                "test.txt",
                "text/plain",
                10L,
                "09/04/2026",
                "PENDING"
        );
        when(fileQueryService.listActiveFiles(any())).thenReturn(List.of(response));

        this.mockMvc.perform(get(LIST_FILES)
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uploadId").value("id"))
                .andExpect(jsonPath("$[0].s3Key").value("s3key"))
                .andExpect(jsonPath("$[0].fileName").value("test.txt"))
                .andExpect(jsonPath("$[0].fileType").value("text/plain"))
                .andExpect(jsonPath("$[0].fileSize").value(10L))
                .andExpect(jsonPath("$[0].uploadDate").value("09/04/2026"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }


    @DisplayName( "Should be successful when listing history")
    @Test
    public void shouldBe200WhenListingHistory() throws Exception{
        var response = new FileListResponse(
                "id",
                "s3key",
                "test.txt",
                "text/plain",
                10L,
                "09/04/2026",
                "PENDING"
        );
        when(fileQueryService.listAllFiles(any())).thenReturn(List.of(response));
        this.mockMvc.perform(get(HISTORY)
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uploadId").value("id"))
                .andExpect(jsonPath("$[0].s3Key").value("s3key"))
                .andExpect(jsonPath("$[0].fileName").value("test.txt"))
                .andExpect(jsonPath("$[0].fileType").value("text/plain"))
                .andExpect(jsonPath("$[0].fileSize").value(10L))
                .andExpect(jsonPath("$[0].uploadDate").value("09/04/2026"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @DisplayName( "Should be successful when download file")
    @Test
    public void shoudBe200WhenDownloadFile() throws Exception{
        var response = new FileDownloadResponse(
                "FileId",
                "FileName",
                "preSignedUrl"
        );
        when(fileQueryService.getDownloadUrl(any(), any())).thenReturn(response);
        this.mockMvc.perform(get(DOWNLOAD_FILES, "id")
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("FileId"))
                .andExpect(jsonPath("$.fileName").value("FileName"))
                .andExpect(jsonPath("$.preSignedUrl").value("preSignedUrl"));
    }

    @DisplayName( "Should be successful when delete file")
    @Test
    public void shouBe204WhenDeleteFile() throws Exception {
        doNothing().when(fileDeleteService).deleteFile(any(), any());
        this.mockMvc.perform(delete(DELETE_FILES, "id")
                        .contentType("application/json"))
                .andExpect(status().isNoContent());
    }

}


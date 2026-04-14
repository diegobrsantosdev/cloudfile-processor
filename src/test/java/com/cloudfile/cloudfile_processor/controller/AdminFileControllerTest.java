package com.cloudfile.cloudfile_processor.controller;

import com.cloudfile.cloudfile_processor.dto.FileListResponse;
import com.cloudfile.cloudfile_processor.enums.UploadStatus;
import com.cloudfile.cloudfile_processor.exceptions.FileNotFoundException;
import com.cloudfile.cloudfile_processor.exceptions.FileOperationException;
import com.cloudfile.cloudfile_processor.exceptions.GlobalExceptionHandler;
import com.cloudfile.cloudfile_processor.model.FileMetadata;
import com.cloudfile.cloudfile_processor.service.FileDeleteService;
import com.cloudfile.cloudfile_processor.service.FileProcessingService;
import com.cloudfile.cloudfile_processor.service.FileQueryService;
import com.cloudfile.cloudfile_processor.service.FileUploadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({GlobalExceptionHandler.class,})
@WebMvcTest(AdminFileController.class)
@ActiveProfiles("test")
class AdminFileControllerTest {

    private static final String LIST_USERS = "/api/v1/admin/files/users";
    private static final String LIST_FILES = "/api/v1/admin/files/users/{userId}";
    private static final String REPROCESS_FILE = "/api/v1/admin/files/{fileId}/reprocess";
    private static final String DELETE_FILE = "/api/v1/admin/files/{fileId}";

    //Bug fixed 2
    //Bug fixed

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    FileUploadService fileUploadService;

    @MockBean
    FileQueryService fileQueryService;

    @MockBean
    FileProcessingService fileProcessingService;

    @MockBean
    FileDeleteService fileDeleteService;



    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return empty list when no users exist")
    public void shouldReturnEmptyUsersListWhenNoUsersExist() throws Exception {

        when(fileQueryService.listAllUsers()).thenReturn(new ArrayList<>());

        mockMvc.perform(get(LIST_USERS)
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return users list when users exist")
    public void shouldReturnUsersListWhenAdminRequests() throws Exception {
        var users = List.of("user1", "user2");

        when(fileQueryService.listAllUsers()).thenReturn(users);

        mockMvc.perform(get(LIST_USERS)
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("user1"))
                .andExpect(jsonPath("$[1]").value("user2"));
    }


    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return list of files by user id")
    public void shouldReturnListFilesWithMultipleFiles() throws Exception {

        var userId = "user1";

        var file1 = new FileListResponse(
                "id1",
                "key1",
                "file1.txt",
                "text/plain",
                100L,
                "09/04/2026",
                "PENDING"
        );

        var file2 = new FileListResponse(
                "id2",
                "key2",
                "file2.txt",
                "image/png",
                200L,
                "10/04/2026",
                "COMPLETED"
        );

        when(fileQueryService.listAllFiles(any()))
                .thenReturn(List.of(file1, file2));

       mockMvc.perform(get(LIST_FILES, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uploadId").value("id1"))
                .andExpect(jsonPath("$[1].uploadId").value("id2"))
                .andExpect(jsonPath("$[0].s3Key").value("key1"))
                .andExpect(jsonPath("$[1].s3Key").value("key2"))
                .andExpect(jsonPath("$[0].fileName").value("file1.txt"))
                .andExpect(jsonPath("$[1].fileName").value("file2.txt"))
                .andExpect(jsonPath("$[0].fileType").value("text/plain"))
                .andExpect(jsonPath("$[1].fileType").value("image/png"))
                .andExpect(jsonPath("$[0].fileSize").value(100L))
                .andExpect(jsonPath("$[1].fileSize").value(200L))
                .andExpect(jsonPath("$[0].uploadDate").value("09/04/2026"))
                .andExpect(jsonPath("$[1].uploadDate").value("10/04/2026"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return list without files")
    public void shouldReturnListFilesWithoutFiles() throws Exception {

        when(fileQueryService.listAllFiles(any()))
                .thenReturn(List.of());

        mockMvc.perform(get(LIST_FILES, "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should reprocess file by FileId")
    public void shouldReprocessFile() throws Exception{

        var fileId = "fileId";

        doNothing().when(fileProcessingService)
                .forceReprocessByFileId(any());

        mockMvc.perform(post(REPROCESS_FILE, fileId)
                .with(csrf()))
                .andExpect(status().isNoContent());
   }


    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return 500 when trying to reprocess a deleted file")
    public void shouldReturn500WhenReprocessDeletedFile() throws Exception {

        var fileId = "fileId";
        var metadata = new FileMetadata();

        metadata.setStatus(UploadStatus.DELETED.name());

        when(fileQueryService.getFileMetadataByFileId(any()))
                .thenReturn(metadata);

        doThrow(new IllegalStateException("Cannot reprocess a deleted file"))
                .when(fileProcessingService).forceReprocessByFileId(any());

        mockMvc.perform(post(REPROCESS_FILE, fileId)
                .with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should delete file successfully")
    public void shouldDeleteFileSuccessfully() throws Exception{

        var fileId = "fileId";
        doNothing().when(fileDeleteService).deleteFileByFileId(any());

        mockMvc.perform(delete(DELETE_FILE, fileId)
                .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return 404 when trying to delete a no-existent user")
    public void shouldReturn404WhenDeletingNonExistentUser() throws Exception{

        var fileId = "fileId";

        doThrow(new FileNotFoundException("File not found: "))
                .when(fileDeleteService).deleteFileByFileId(any());

        mockMvc.perform(delete(DELETE_FILE, fileId)
                .with(csrf()))
                .andExpect(status().isNotFound());

    }

       @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should return 500 when service throws exception")
    public void shouldReturn500WhenServiceFails() throws Exception {

        doThrow(new FileOperationException("Failed to retrieve users", null))
                .when(fileQueryService).listAllUsers();

        mockMvc.perform(get(LIST_USERS)
                        .contentType("application/json"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to retrieve users"));
    }



}



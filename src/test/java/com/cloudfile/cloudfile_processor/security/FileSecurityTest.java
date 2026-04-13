package com.cloudfile.cloudfile_processor.security;

import com.cloudfile.cloudfile_processor.controller.AdminFileController;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(AdminFileController.class)
@Import(SecurityConfigFilterTest.class)
public class FileSecurityTest {

    private static final String LIST_USERS = "/api/v1/admin/files/users";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    FileQueryService fileQueryService;

    @MockBean
    FileDeleteService fileDeleteService;

    @MockBean
    FileProcessingService fileProcessingService;

    @MockBean
    FileUploadService fileUploadService;

    @Test
    @DisplayName("Should allow ADMIN access")
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminAccess() throws Exception {

        mockMvc.perform(get(LIST_USERS))
                .andExpect(status().isOk());
    }


    @Test
    @DisplayName("Should return 403 when USER tries to access admin endpoint")
    @WithMockUser(roles = "USER")
    void shouldReturn403WhenUserIsNotAdmin() throws Exception {

        mockMvc.perform(get(LIST_USERS))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 401 when user is not authenticated")
    public void shouldReturn401WhenNotAuthenticated() throws Exception {

        mockMvc.perform(get(LIST_USERS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 401 when no authentication is provided")
    void shouldReturn401WhenNoAuth() throws Exception {

        mockMvc.perform(get(LIST_USERS))
                .andExpect(status().isUnauthorized());
    }

}

package com.cloudfile.cloudfile_processor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Endpoints {

    //USER
    FILE_UPLOAD("/api/v1/files"),
    FILE_LIST("/api/v1/files"),
    FILE_HISTORY("/api/v1/files/history"),
    FILE_DOWNLOAD_BY_ID("/api/v1/files/*"),
    FILE_DELETE_BY_ID("/api/v1/files/*"),

    //ADMIN
    ADMIN_LIST_USERS("/api/v1/admin/files/users"),
    ADMIN_LIST_USER_FILES("/api/v1/admin/files/users/*"),
    ADMIN_FILE_REPROCESS("/api/v1/admin/files/*/reprocess"),
    ADMIN_FILE_DELETE_BY_ID("/api/v1/admin/files/*"),

    //INFRA
    SWAGGER("/swagger-ui/**"),
    SWAGGER_API("/v3/api-docs/**"),
    ACTUATOR("/actuator/**");

    private final String url;

}

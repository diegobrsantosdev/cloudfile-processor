package com.cloudfile.cloudfile_processor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Endpoints {
    FILE_UPLOAD("/api/v1/files"),
    FILE_LIST("/api/v1/files"),
    FILE_HISTORY("/api/v1/files/history"),
    FILE_DOWNLOAD("/api/v1/files/*"),
    FILE_DELETE("/api/v1/files/*"),
    SWAGGER("/swagger-ui/**"),
    SWAGGER_API("/v3/api-docs/**"),
    ACTUATOR("/actuator/**");

    private final String url;

}

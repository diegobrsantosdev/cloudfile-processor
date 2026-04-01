package com.cloudfile.cloudfile_processor.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RolePermissions {

    USER("USER"),
    ADMIN("ADMIN");

    private final String role;
}

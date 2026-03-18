package com.cloudfile.cloudfile_processor.dto;

import java.util.Map;

public record ErrorResponse(
        String error,
        Map<String, String> fields
) {}

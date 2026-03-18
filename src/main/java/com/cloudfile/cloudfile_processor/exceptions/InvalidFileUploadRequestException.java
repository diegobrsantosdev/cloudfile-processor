package com.cloudfile.cloudfile_processor.exceptions;

public class InvalidFileUploadRequestException extends RuntimeException {
    public InvalidFileUploadRequestException(String message) {
        super(message);
    }
}

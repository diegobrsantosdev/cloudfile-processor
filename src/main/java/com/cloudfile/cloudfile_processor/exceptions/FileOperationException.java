package com.cloudfile.cloudfile_processor.exceptions;

public class FileOperationException extends RuntimeException {
    public FileOperationException(String message) {
        super(message);
    }
    public FileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

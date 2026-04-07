package com.cloudfile.cloudfile_processor.exceptions;

public class AdminOperationException extends RuntimeException {
    public AdminOperationException(String message) {
        super(message);
    }
    public AdminOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

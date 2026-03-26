package com.cloudfile.cloudfile_processor.exceptions;

public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String fileId) {
        super("File not found: " + fileId);
    }
}

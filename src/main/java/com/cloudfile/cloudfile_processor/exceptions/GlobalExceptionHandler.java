package com.cloudfile.cloudfile_processor.exceptions;

import com.cloudfile.cloudfile_processor.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends RuntimeException {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> fields = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.put(error.getField(), error.getDefaultMessage())
        );

        ErrorResponse response = new ErrorResponse("Validation failed", fields);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(InvalidFileUploadRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            InvalidFileUploadRequestException ex) {

        ErrorResponse response = new ErrorResponse(ex.getMessage(), null);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(FileUploadProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessingError(
            FileUploadProcessingException ex) {

        ErrorResponse response = new ErrorResponse("Upload processing failed", null);

        return ResponseEntity.status(500).body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse response = new ErrorResponse(
                "Internal server error",
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}

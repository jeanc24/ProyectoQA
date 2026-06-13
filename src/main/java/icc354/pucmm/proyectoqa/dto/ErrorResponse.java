package icc354.pucmm.proyectoqa.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        Instant timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Instant.now(), null);
    }

    public static ErrorResponse of(
            int status,
            String error,
            String message,
            List<FieldError> fieldErrors) {

        return new ErrorResponse(status, error, message, Instant.now(), fieldErrors);
    }
}
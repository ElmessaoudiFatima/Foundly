package com.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse d'erreur standard — alignée sur le schéma OpenAPI "ApiError".
 * Champs : timestamp, status, code, message, path, traceId, fieldErrors.
 */
public class ApiError {

    private Instant timestamp;
    private int status;
    private String code;
    private String message;
    private String path;
    private String traceId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<FieldError> fieldErrors;

    public ApiError() {}

    public ApiError(int status, String code, String message, String path) {
        this.timestamp = Instant.now();
        this.status    = status;
        this.code      = code;
        this.message   = message;
        this.path      = path;
        this.traceId   = UUID.randomUUID().toString().substring(0, 8);
    }

    // Getters / Setters

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public List<FieldError> getFieldErrors() { return fieldErrors; }
    public void setFieldErrors(List<FieldError> fieldErrors) { this.fieldErrors = fieldErrors; }

    /**
     * Détail d'une erreur de validation sur un champ spécifique.
     */
    public static class FieldError {
        private String field;
        private String message;

        public FieldError() {}

        public FieldError(String field, String message) {
            this.field   = field;
            this.message = message;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}

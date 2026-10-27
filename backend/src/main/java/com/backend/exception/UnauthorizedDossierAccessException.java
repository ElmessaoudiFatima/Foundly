package com.backend.exception;

public class UnauthorizedDossierAccessException extends RuntimeException {
    public UnauthorizedDossierAccessException(String message) {
        super(message);
    }
}

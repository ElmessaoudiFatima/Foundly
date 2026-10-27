package com.backend.exception;

public class DossierNotFoundException extends RuntimeException {
    public DossierNotFoundException(String message) {
        super(message);
    }
}

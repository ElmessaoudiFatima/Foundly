package com.backend.exception;

public class InvalidDossierStateException extends RuntimeException {
    public InvalidDossierStateException(String message) {
        super(message);
    }
}

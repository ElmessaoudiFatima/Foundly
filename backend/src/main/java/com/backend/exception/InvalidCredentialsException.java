package com.backend.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Identifiants invalides.");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}

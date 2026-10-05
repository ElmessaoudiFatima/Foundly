package com.backend.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Un compte avec cet email existe déjà.");
    }

    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}

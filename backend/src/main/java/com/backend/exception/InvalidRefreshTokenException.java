package com.backend.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Jeton de renouvellement invalide, expiré ou révoqué.");
    }

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}

package com.backend.exception;

public class AgentClaimConflictException extends RuntimeException {
    public AgentClaimConflictException(String message) {
        super(message);
    }
}

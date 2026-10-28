package com.backend.exception;

import java.util.UUID;

public class WorkflowVersionNotFoundException extends RuntimeException {

    public WorkflowVersionNotFoundException(UUID id) {
        super("Version de workflow introuvable : " + id);
    }

    public WorkflowVersionNotFoundException(String message) {
        super(message);
    }
}

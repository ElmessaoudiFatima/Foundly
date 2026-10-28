package com.backend.exception;

import java.util.UUID;

public class WorkflowVersionNotEditableException extends RuntimeException {

    public WorkflowVersionNotEditableException(UUID id) {
        super("Impossible de modifier une version non-BROUILLON. Créez une nouvelle version. (id=" + id + ")");
    }

    public WorkflowVersionNotEditableException(String message) {
        super(message);
    }
}

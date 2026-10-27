package com.backend.exception;

import java.util.List;

public class IncompleteChecklistException extends RuntimeException {
    private final List<String> missingDocuments;

    public IncompleteChecklistException(String message, List<String> missingDocuments) {
        super(message);
        this.missingDocuments = missingDocuments;
    }

    public List<String> getMissingDocuments() {
        return missingDocuments;
    }
}

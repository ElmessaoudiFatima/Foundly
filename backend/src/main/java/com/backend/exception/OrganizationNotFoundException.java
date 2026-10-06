package com.backend.exception;

/**
 * Levée quand aucun organisme ne correspond au code fourni.
 * → 404 ORGANIZATION_NOT_FOUND
 */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(String code) {
        super("Aucun organisme trouvé avec le code '" + code + "'.");
    }
}

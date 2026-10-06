package com.backend.exception;

/**
 * Levée quand on tente de créer un organisme avec un code déjà utilisé.
 * → 409 ORGANIZATION_CODE_ALREADY_USED
 */
public class OrganizationCodeAlreadyUsedException extends RuntimeException {

    public OrganizationCodeAlreadyUsedException(String code) {
        super("Un organisme avec le code '" + code + "' existe déjà.");
    }
}

package com.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    @DisplayName("generateDefaultPassword : génère un mot de passe d'au moins 12 caractères avec mix de types")
    void generateDefaultPassword_validComplexity() {
        String password = PasswordGenerator.generateDefaultPassword();

        assertThat(password).isNotNull();
        assertThat(password.length()).isGreaterThanOrEqualTo(12);

        // Au moins une majuscule
        assertThat(password.chars().anyMatch(Character::isUpperCase)).isTrue();
        // Au moins une minuscule
        assertThat(password.chars().anyMatch(Character::isLowerCase)).isTrue();
        // Au moins un chiffre
        assertThat(password.chars().anyMatch(Character::isDigit)).isTrue();
        // Au moins un caractère spécial
        assertThat(password.chars().anyMatch(ch -> "!@#$%&*+=-".indexOf(ch) >= 0)).isTrue();
    }
}

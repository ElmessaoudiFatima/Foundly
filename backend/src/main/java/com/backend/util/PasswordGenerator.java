package com.backend.util;

import java.security.SecureRandom;

public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // lettres majuscules sans ambiguïté
    private static final String LOWER = "abcdefghjkmnpqrstuvwxyz"; // lettres minuscules
    private static final String DIGITS = "23456789";                  // chiffres lisibles
    private static final String SPECIAL = "!@#$%&*+=-";               // caractères spéciaux courants
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {}

    /**
     * Génère un mot de passe temporaire robuste et aléatoire.
     * Garantit la présence d'au moins 1 majuscule, 1 minuscule, 1 chiffre et 1 symbole.
     */
    public static String generateRandomPassword(int length) {
        if (length < 8) {
            length = 12;
        }

        StringBuilder sb = new StringBuilder(length);
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = 4; i < length; i++) {
            sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }

        return new String(chars);
    }

    public static String generateDefaultPassword() {
        return generateRandomPassword(12);
    }
}

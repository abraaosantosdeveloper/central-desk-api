package br.com.contabilizape.centraldesk.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Senhas temporárias de 16 caracteres (SecureRandom), sem caracteres que se confundem ao ditar ou
 * copiar (0/O, 1/l/I). Sempre com letra e número, como exige a regra de senha do cadastro.
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 16;
    private static final String LETTERS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        while (true) {
            StringBuilder password = new StringBuilder(LENGTH);
            for (int i = 0; i < LENGTH; i++) {
                password.append(ALL.charAt(random.nextInt(ALL.length())));
            }
            String candidate = password.toString();
            if (candidate.chars().anyMatch(c -> LETTERS.indexOf(c) >= 0) && candidate.chars().anyMatch(c -> DIGITS.indexOf(c) >= 0)) {
                return candidate;
            }
        }
    }
}

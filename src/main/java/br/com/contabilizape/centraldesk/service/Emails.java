package br.com.contabilizape.centraldesk.service;

import java.util.Locale;

/**
 * E-mails são gravados e buscados sempre na mesma forma: sem espaços nas pontas e em minúsculas.
 */
final class Emails {

    private Emails() {
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

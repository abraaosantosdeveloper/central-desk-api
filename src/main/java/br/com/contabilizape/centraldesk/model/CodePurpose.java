package br.com.contabilizape.centraldesk.model;

import br.com.contabilizape.centraldesk.email.EmailTemplate;

import java.time.Duration;

/**
 * Para que serve um código enviado por e-mail. A finalidade entra no HMAC: o código de uma
 * finalidade nunca vale para outra (PLANO-SEGURANCA §1).
 */
public enum CodePurpose {
    EMAIL_VERIFICATION(Duration.ofMinutes(10), EmailTemplate.EMAIL_VERIFICATION_CODE),
    PASSWORD_RESET(Duration.ofMinutes(10), EmailTemplate.PASSWORD_RESET_CODE),
    LOGIN_TFA(Duration.ofMinutes(5), EmailTemplate.LOGIN_CODE),
    TFA_DISABLE(Duration.ofMinutes(10), EmailTemplate.TWO_FACTOR_DISABLE_CODE),
    PASSWORD_CHANGE(Duration.ofMinutes(10), EmailTemplate.PASSWORD_CHANGE_CODE);

    private final Duration validity;
    private final EmailTemplate template;

    CodePurpose(Duration validity, EmailTemplate template) {
        this.validity = validity;
        this.template = template;
    }

    public Duration validity() {
        return validity;
    }

    public EmailTemplate template() {
        return template;
    }
}

package br.com.contabilizape.centraldesk.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.Locale;

/**
 * O Spring cria este validador como bean, então o domínio chega pelo construtor a partir da configuração.
 * Fora do Spring (testes unitários), basta instanciá-lo passando o domínio.
 */
public class CorporateEmailValidator implements ConstraintValidator<CorporateEmail, String> {

    private final String allowedDomain;

    public CorporateEmailValidator(@Value("${app.users.allowed-email-domain}") String allowedDomain) {
        this.allowedDomain = allowedDomain.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null) {
            return true;
        }
        // O "@" antes do domínio impede que algo como "x@outracontabilizape.com.br" passe
        boolean valid = email.trim().toLowerCase(Locale.ROOT).endsWith("@" + allowedDomain);
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("deve ser um e-mail do domínio @" + allowedDomain)
                    .addConstraintViolation();
        }
        return valid;
    }
}

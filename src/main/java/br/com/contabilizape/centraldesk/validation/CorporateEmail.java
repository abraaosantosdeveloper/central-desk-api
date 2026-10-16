package br.com.contabilizape.centraldesk.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Exige um e-mail do domínio da empresa, configurado em {@code app.users.allowed-email-domain}.
 * A comparação ignora maiúsculas. Valor nulo é considerado válido: a obrigatoriedade fica com {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = CorporateEmailValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface CorporateEmail {

    String message() default "deve ser um e-mail corporativo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

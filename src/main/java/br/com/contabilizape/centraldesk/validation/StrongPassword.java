package br.com.contabilizape.centraldesk.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regras de senha do sistema: 8 a 72 caracteres (o bcrypt ignora o que passa de 72 bytes),
 * com ao menos uma letra e um número. Usada no cadastro e na troca de senha.
 */
@NotBlank
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).*$", message = "deve conter ao menos uma letra e um número")
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "senha fraca";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

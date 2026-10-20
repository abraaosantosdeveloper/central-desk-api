package br.com.contabilizape.centraldesk.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A senha nova segue as mesmas regras do cadastro (@StrongPassword).
 */
class PasswordChangeRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsValidRequest() {
        assertThat(validator.validate(new PasswordChangeRequest("qualquerCoisa", "novaSenha456"))).isEmpty();
    }

    @Test
    void rejectsBlankCurrentPassword() {
        assertThat(invalidFields(new PasswordChangeRequest(" ", "novaSenha456"))).containsExactly("currentPassword");
    }

    @ParameterizedTest(name = "recusa senha nova {0}")
    @ValueSource(strings = {"curta1", "somenteletras", "1234567890", "a1234567890123456789012345678901234567890123456789012345678901234567890123"})
    void rejectsWeakNewPassword(String newPassword) {
        assertThat(invalidFields(new PasswordChangeRequest("senhaForte123", newPassword))).containsExactly("newPassword");
    }

    private static Set<String> invalidFields(PasswordChangeRequest request) {
        Set<ConstraintViolation<PasswordChangeRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }
}

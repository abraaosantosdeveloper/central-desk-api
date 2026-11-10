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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserCreateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private static final String VALID_NAME = "Maria da Silva";
    private static final String VALID_CPF = "52998224725";
    private static final String VALID_EMAIL = "maria.silva@contabilizape.com.br";
    private static final String VALID_PASSWORD = "senhaForte123";
    private static final UUID VALID_DEPARTMENT = UUID.randomUUID();

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
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, VALID_EMAIL, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankFullName() {
        var request = new UserCreateRequest("  ", VALID_CPF, VALID_EMAIL, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("fullName");
    }

    @Test
    void rejectsFullNameLongerThan150() {
        var request = new UserCreateRequest("a".repeat(151), VALID_CPF, VALID_EMAIL, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("fullName");
    }

    @ParameterizedTest(name = "recusa CPF {0}")
    @ValueSource(strings = {"12345678901", "529.982.247-25", "00000000000"})
    void rejectsInvalidCpf(String cpf) {
        var request = new UserCreateRequest(VALID_NAME, cpf, VALID_EMAIL, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("cpf");
    }

    @Test
    void rejectsMissingCpf() {
        var request = new UserCreateRequest(VALID_NAME, null, VALID_EMAIL, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("cpf");
    }

    @ParameterizedTest(name = "recusa e-mail {0}")
    @ValueSource(strings = {"maria", "maria@", "@contabilizape.com.br"})
    void rejectsMalformedEmail(String email) {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, email, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("email");
    }

    // Versão genérica: o cadastro é aberto, sem restrição de domínio do e-mail
    @ParameterizedTest(name = "aceita e-mail de qualquer domínio {0}")
    @ValueSource(strings = {"maria@gmail.com", "maria@outracontabilizape.com.br", "maria@empresa.com.br"})
    void acceptsEmailFromAnyDomain(String email) {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, email, VALID_PASSWORD, VALID_DEPARTMENT);

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest(name = "recusa senha {0}")
    @ValueSource(strings = {
            "abc123",          // menos de 8 caracteres
            "somenteletras",   // sem número
            "1234567890",      // sem letra
    })
    void rejectsWeakPassword(String password) {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, VALID_EMAIL, password, VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("password");
    }

    @Test
    void rejectsPasswordLongerThan72() {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, VALID_EMAIL, "a1".repeat(37), VALID_DEPARTMENT);

        assertThat(invalidFields(request)).containsExactly("password");
    }

    @Test
    void acceptsPasswordWithAccentedLetter() {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, VALID_EMAIL, "maçã12345", VALID_DEPARTMENT);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingDepartment() {
        var request = new UserCreateRequest(VALID_NAME, VALID_CPF, VALID_EMAIL, VALID_PASSWORD, null);

        assertThat(invalidFields(request)).containsExactly("departmentId");
    }

    private static Set<String> invalidFields(UserCreateRequest request) {
        Set<ConstraintViolation<UserCreateRequest>> violations = validator.validate(request);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}

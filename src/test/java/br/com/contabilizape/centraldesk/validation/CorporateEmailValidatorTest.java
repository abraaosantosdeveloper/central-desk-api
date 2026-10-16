package br.com.contabilizape.centraldesk.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorporateEmailValidatorTest {

    // Mesmo valor de app.users.allowed-email-domain; no Spring ele vem da configuração
    private final CorporateEmailValidator validator = new CorporateEmailValidator("contabilizape.com.br");

    @ParameterizedTest(name = "aceita {0}")
    @ValueSource(strings = {
            "maria.silva@contabilizape.com.br",
            "MARIA.SILVA@ContabilizaPE.com.br",   // domínio sem diferenciar maiúsculas
    })
    void acceptsCompanyEmail(String email) {
        assertThat(validator.isValid(email, mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS))).isTrue();
    }

    @ParameterizedTest(name = "recusa {0}")
    @ValueSource(strings = {
            "maria@gmail.com",
            "maria@outracontabilizape.com.br",       // termina com o domínio, mas é outro domínio
            "maria@contabilizape.com.br.golpe.com",  // contém o domínio no meio
            "maria@sub.contabilizape.com.br",        // subdomínio não é o domínio da empresa
            "maria.contabilizape.com.br",            // sem @
    })
    void rejectsOtherDomains(String email) {
        assertThat(validator.isValid(email, mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS))).isFalse();
    }

    @Test
    @DisplayName("a mensagem de erro informa o domínio exigido")
    void errorMessageNamesTheDomain() {
        ConstraintValidatorContext context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

        validator.isValid("maria@gmail.com", context);

        verify(context).buildConstraintViolationWithTemplate(contains("@contabilizape.com.br"));
    }

    @Test
    @DisplayName("considera nulo válido: a obrigatoriedade é papel do @NotBlank")
    void treatsNullAsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}

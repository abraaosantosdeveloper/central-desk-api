package br.com.contabilizape.centraldesk.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfValidatorTest {

    private final CpfValidator validator = new CpfValidator();

    @ParameterizedTest(name = "aceita CPF válido {0}")
    @ValueSource(strings = {"52998224725", "11144477735", "12345678909"})
    void acceptsValidCpf(String cpf) {
        assertThat(validator.isValid(cpf, null)).isTrue();
    }

    @ParameterizedTest(name = "recusa CPF com dígito verificador errado {0}")
    @ValueSource(strings = {"52998224724", "52998224715", "12345678901"})
    void rejectsWrongCheckDigits(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @ParameterizedTest(name = "recusa CPF com todos os dígitos iguais {0}")
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    void rejectsRepeatedDigits(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @ParameterizedTest(name = "recusa CPF fora do formato {0}")
    @ValueSource(strings = {"5299822472", "529982247250", "529.982.247-25", "5299822472a", " 52998224725", ""})
    void rejectsWrongFormat(String cpf) {
        assertThat(validator.isValid(cpf, null)).isFalse();
    }

    @Test
    @DisplayName("considera nulo válido: a obrigatoriedade é papel do @NotBlank")
    void treatsNullAsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}

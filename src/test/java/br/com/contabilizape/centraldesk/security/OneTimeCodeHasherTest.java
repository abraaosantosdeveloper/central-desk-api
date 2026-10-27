package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.model.CodePurpose;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HMAC-SHA256 dos códigos: o banco vazado não revela código nenhum, e o código de uma finalidade
 * ou de um usuário não serve para outra.
 */
class OneTimeCodeHasherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("chave-de-teste-dos-codigos-32byt".getBytes());
    private static final UUID MARIA = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final UUID JOAO = UUID.fromString("7a1d9e2b-3c4f-4a5b-8c6d-9e0f1a2b3c4d");

    private final OneTimeCodeHasher hasher = new OneTimeCodeHasher(KEY);

    @Test
    void sameInputGivesTheSameTagAndTheTagHidesTheCode() {
        String tag = hasher.tag(CodePurpose.LOGIN_TFA, MARIA, "042917");

        assertThat(tag).hasSize(64).matches("[0-9a-f]+").doesNotContain("042917");
        assertThat(hasher.matches(tag, CodePurpose.LOGIN_TFA, MARIA, "042917")).isTrue();
    }

    @Test
    void codeOfAnotherPurposeOrUserDoesNotMatch() {
        String tag = hasher.tag(CodePurpose.PASSWORD_RESET, MARIA, "042917");

        assertThat(hasher.matches(tag, CodePurpose.LOGIN_TFA, MARIA, "042917")).isFalse();
        assertThat(hasher.matches(tag, CodePurpose.PASSWORD_RESET, JOAO, "042917")).isFalse();
        assertThat(hasher.matches(tag, CodePurpose.PASSWORD_RESET, MARIA, "042918")).isFalse();
    }

    @Test
    void anotherKeyGivesAnotherTag() {
        OneTimeCodeHasher other = new OneTimeCodeHasher(
                Base64.getEncoder().encodeToString("outra-chave-de-teste-dos-codigos".getBytes()));

        assertThat(other.tag(CodePurpose.LOGIN_TFA, MARIA, "042917"))
                .isNotEqualTo(hasher.tag(CodePurpose.LOGIN_TFA, MARIA, "042917"));
    }

    @Test
    void missingOrShortKeyStopsTheApplication() {
        assertThatThrownBy(() -> new OneTimeCodeHasher("")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP_HMAC_KEY");
        assertThatThrownBy(() -> new OneTimeCodeHasher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OneTimeCodeHasher("não é base64!")).isInstanceOf(IllegalStateException.class);
    }
}

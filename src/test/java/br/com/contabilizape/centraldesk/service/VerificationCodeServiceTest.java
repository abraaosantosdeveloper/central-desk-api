package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.email.EmailDispatcher;
import br.com.contabilizape.centraldesk.email.EmailTemplate;
import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.VerificationCode;
import br.com.contabilizape.centraldesk.repository.VerificationCodeRepository;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import br.com.contabilizape.centraldesk.security.OneTimeCodeHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Política única dos códigos (PLANO-SEGURANCA §4): 6 dígitos, validade, 5 tentativas, uso único,
 * novo pedido invalida o anterior, 60 s entre pedidos, 5 por hora por conta e limite por IP.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private static final OffsetDateTime NOW_UTC = NOW.atOffset(ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.fromString("0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f");
    private static final String IP = "200.10.20.30";

    @Mock private VerificationCodeRepository repository;
    @Mock private EmailDispatcher dispatcher;
    @Mock private AccountSecurityPolicy policy;

    private final OneTimeCodeHasher hasher =
            new OneTimeCodeHasher(Base64.getEncoder().encodeToString("chave-de-teste-dos-codigos-32byt".getBytes()));
    private final User maria = user();
    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        service = new VerificationCodeService(repository, dispatcher, policy, hasher, Clock.fixed(NOW, ZoneOffset.UTC));
        when(policy.codeRequestsPerIpPerHour()).thenReturn(20);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------- enviar ----------

    @Test
    void sendsASixDigitCodeByEmailAndStoresOnlyTheHmac() {
        service.send(maria, CodePurpose.PASSWORD_RESET, IP);

        VerificationCode saved = saved();
        String code = sentVariables(EmailTemplate.PASSWORD_RESET_CODE).get("code").toString();
        assertThat(code).matches("\\d{6}");
        assertThat(saved.getCodeHmac()).hasSize(64).doesNotContain(code);
        assertThat(hasher.matches(saved.getCodeHmac(), CodePurpose.PASSWORD_RESET, USER_ID, code)).isTrue();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW_UTC.plusMinutes(10));
        assertThat(saved.getRequestIp()).isEqualTo(IP);
        assertThat(sentVariables(EmailTemplate.PASSWORD_RESET_CODE)).containsEntry("validityMinutes", 10L)
                .containsEntry("recipientName", "Maria da Silva");
    }

    @Test
    void loginCodeIsValidForFiveMinutes() {
        service.send(maria, CodePurpose.LOGIN_TFA, IP);

        assertThat(saved().getExpiresAt()).isEqualTo(NOW_UTC.plusMinutes(5));
        assertThat(sentVariables(EmailTemplate.LOGIN_CODE)).containsEntry("validityMinutes", 5L);
    }

    @Test
    void codesAreNotPredictable() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(service.generateCode());
        }
        assertThat(codes).allMatch(code -> code.matches("\\d{6}")).hasSizeGreaterThan(45);
    }

    @Test
    void aNewRequestInvalidatesThePreviousCodes() {
        VerificationCode previous = new VerificationCode(maria, CodePurpose.PASSWORD_RESET, "a".repeat(64),
                NOW_UTC.plusMinutes(5), IP);
        ReflectionTestUtils.setField(previous, "createdAt", NOW_UTC.minusMinutes(5));
        when(repository.findByUserIdAndPurposeAndConsumedAtIsNull(USER_ID, CodePurpose.PASSWORD_RESET))
                .thenReturn(List.of(previous));

        service.send(maria, CodePurpose.PASSWORD_RESET, IP);

        assertThat(previous.isUsableAt(NOW_UTC)).isFalse();
    }

    @Test
    void refusesAnotherRequestWithinSixtySeconds() {
        when(repository.findLatestCreatedAt(USER_ID, CodePurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(NOW_UTC.minusSeconds(40)));

        assertThatThrownBy(() -> service.send(maria, CodePurpose.PASSWORD_RESET, IP))
                .isInstanceOf(CodeRequestLimitException.class)
                .satisfies(e -> assertThat(((CodeRequestLimitException) e).getRetryAfter()).isEqualTo(Duration.ofSeconds(20)));
        verify(dispatcher, never()).dispatch(any(), anyString(), any(), anyString());
    }

    @Test
    void refusesTheSixthRequestInAnHour() {
        when(repository.findLatestCreatedAt(USER_ID, CodePurpose.LOGIN_TFA)).thenReturn(Optional.of(NOW_UTC.minusMinutes(5)));
        when(repository.countByUserIdAndPurposeAndCreatedAtAfter(USER_ID, CodePurpose.LOGIN_TFA, NOW_UTC.minusHours(1)))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.send(maria, CodePurpose.LOGIN_TFA, IP)).isInstanceOf(CodeRequestLimitException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void refusesWhenTheSameIpAskedTooManyCodes() {
        when(repository.countByRequestIpAndCreatedAtAfter(IP, NOW_UTC.minusHours(1))).thenReturn(20L);

        assertThatThrownBy(() -> service.send(maria, CodePurpose.EMAIL_VERIFICATION, IP))
                .isInstanceOf(CodeRequestLimitException.class);
        verify(dispatcher, never()).dispatch(any(), anyString(), any(), anyString());
    }

    // ---------- conferir ----------

    @Test
    void rightCodeIsAcceptedOnceOnly() {
        VerificationCode code = storedCode(CodePurpose.PASSWORD_RESET, "042917", NOW_UTC.plusMinutes(5));

        assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, "042917")).isTrue();
        assertThat(code.isUsableAt(NOW_UTC)).isFalse();
    }

    @Test
    void wrongCodeCountsAnAttemptAndTheFifthKillsTheCode() {
        VerificationCode code = storedCode(CodePurpose.PASSWORD_RESET, "042917", NOW_UTC.plusMinutes(5));

        for (int i = 0; i < 5; i++) {
            assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, "000000")).isFalse();
        }

        assertThat(code.getAttempts()).isEqualTo(5);
        // Esgotou: nem o código certo vale mais
        assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, "042917")).isFalse();
    }

    @Test
    void expiredCodeIsRefused() {
        storedCode(CodePurpose.PASSWORD_RESET, "042917", NOW_UTC.minusSeconds(1));

        assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, "042917")).isFalse();
    }

    @Test
    void codeOfAnotherPurposeIsRefused() {
        storedCode(CodePurpose.PASSWORD_RESET, "042917", NOW_UTC.plusMinutes(5));
        when(repository.findActiveForUpdate(USER_ID, CodePurpose.LOGIN_TFA)).thenReturn(Optional.empty());

        assertThat(service.verify(USER_ID, CodePurpose.LOGIN_TFA, "042917")).isFalse();
    }

    @Test
    void malformedCodeIsRefusedWithoutTouchingTheStoredOne() {
        VerificationCode code = storedCode(CodePurpose.PASSWORD_RESET, "042917", NOW_UTC.plusMinutes(5));

        assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, null)).isFalse();
        assertThat(service.verify(USER_ID, CodePurpose.PASSWORD_RESET, "12ab56")).isFalse();
        assertThat(code.getAttempts()).isEqualTo(2);
    }

    // ---------- auxiliares ----------

    private VerificationCode storedCode(CodePurpose purpose, String code, OffsetDateTime expiresAt) {
        VerificationCode stored = new VerificationCode(maria, purpose, hasher.tag(purpose, USER_ID, code), expiresAt, IP);
        when(repository.findActiveForUpdate(USER_ID, purpose)).thenReturn(Optional.of(stored));
        return stored;
    }

    private VerificationCode saved() {
        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentVariables(EmailTemplate template) {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatch(eq(template), eq("maria.silva@contabilizape.com.br"), captor.capture(), anyString());
        return captor.getValue();
    }

    private static User user() {
        User user = new User("Maria da Silva", "52998224725", "maria.silva@contabilizape.com.br", "hash",
                mock(Department.class));
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}

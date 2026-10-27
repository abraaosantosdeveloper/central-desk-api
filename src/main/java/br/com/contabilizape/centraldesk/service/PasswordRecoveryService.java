package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.PasswordResetRequest;
import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.exception.InvalidVerificationCodeException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * "Esqueci minha senha" (PLANO-SEGURANCA §7). O pedido responde sempre igual, exista a conta ou não (B7).
 * Conta bloqueada não redefine pelo e-mail: recebe um aviso para procurar a Inovação (S1).
 * Código errado aqui não conta para o bloqueio (contar permitiria bloquear alguém sem saber nada).
 */
@Service
public class PasswordRecoveryService {

    private static final int MAX_BLOCKED_NOTICES_PER_HOUR = 3;
    private static final Duration HOUR = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final VerificationCodeService codes;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecurityEventService securityEvents;
    private final AccountEmails accountEmails;
    private final AccountSecurityPolicy policy;
    private final Clock clock;

    public PasswordRecoveryService(UserRepository userRepository, VerificationCodeService codes,
                                   PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                   SecurityEventService securityEvents, AccountEmails accountEmails,
                                   AccountSecurityPolicy policy, Clock clock) {
        this.userRepository = userRepository;
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.securityEvents = securityEvents;
        this.accountEmails = accountEmails;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Manda o código (conta ativa), o aviso de conta bloqueada, ou nada. O único erro possível é o 429 do
     * limite por IP, que não diz nada sobre a conta.
     */
    @Transactional(noRollbackFor = CodeRequestLimitException.class)
    public void requestReset(String rawEmail, ClientInfo client) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (securityEvents.countByIpSince(client.ip(), SecurityEventType.PASSWORD_RESET_REQUESTED, now.minus(HOUR))
                >= policy.codeRequestsPerIpPerHour()) {
            throw new CodeRequestLimitException(HOUR);
        }
        String email = Emails.normalize(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        securityEvents.record(SecurityEventType.PASSWORD_RESET_REQUESTED, user, client,
                user == null ? "e-mail desconhecido: " + email : "conta " + user.getStatus());
        if (user == null) {
            return;
        }
        switch (user.getStatus()) {
            case ACTIVE -> {
                try {
                    codes.send(user, CodePurpose.PASSWORD_RESET, client.ip());
                } catch (CodeRequestLimitException ignored) {
                    // Pedido repetido cedo demais: o código anterior continua valendo; a resposta não muda
                }
            }
            case BLOCKED -> {
                if (securityEvents.countByUserSince(user, SecurityEventType.PASSWORD_RESET_BLOCKED_ACCOUNT, now.minus(HOUR))
                        < MAX_BLOCKED_NOTICES_PER_HOUR) {
                    accountEmails.blockedAccountResetAttempt(user);
                }
                securityEvents.record(SecurityEventType.PASSWORD_RESET_BLOCKED_ACCOUNT, user, client, null);
            }
            default -> {
                // Desativada, anonimizada ou cadastro não confirmado: nada a enviar
            }
        }
    }

    /**
     * Confere o código e grava a senha nova. Tudo cai junto: sessões, navegadores confiáveis e a senha
     * temporária, se houver. Erro no código: 400 sem dizer se venceu, foi usado ou nunca existiu.
     */
    @Transactional
    public void reset(PasswordResetRequest request, ClientInfo client) {
        User user = userRepository.findByEmail(Emails.normalize(request.email()))
                .filter(User::isActive)
                .orElse(null);
        if (user == null || !codes.verify(user.getId(), CodePurpose.PASSWORD_RESET, request.code())) {
            throw new InvalidVerificationCodeException();
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.endAllSessions(user);
        securityEvents.record(SecurityEventType.PASSWORD_RESET_COMPLETED, user, client, null);
        accountEmails.passwordChanged(user);
    }
}

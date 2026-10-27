package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.EmailVerificationRequest;
import br.com.contabilizape.centraldesk.exception.CodeRequestLimitException;
import br.com.contabilizape.centraldesk.exception.InvalidVerificationCodeException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Confirmação do e-mail no cadastro (PLANO-SEGURANCA §8). O código chegou na caixa de entrada, então a
 * confirmação já prova a identidade: a sessão é aberta em seguida, sem pedir a senha de novo.
 */
@Service
public class EmailVerificationService {

    /** Cadastro não confirmado depois disto é apagado pela faxina diária (B3). */
    static final Duration PENDING_LIFETIME = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final VerificationCodeService codes;
    private final UserService userService;
    private final AuthService authService;
    private final SecurityEventService securityEvents;
    private final TrustedDeviceService trustedDevices;
    private final Clock clock;

    public EmailVerificationService(UserRepository userRepository, VerificationCodeService codes,
                                    UserService userService, AuthService authService,
                                    SecurityEventService securityEvents, TrustedDeviceService trustedDevices,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.codes = codes;
        this.userService = userService;
        this.authService = authService;
        this.securityEvents = securityEvents;
        this.trustedDevices = trustedDevices;
        this.clock = clock;
    }

    /**
     * Código certo: ativa a conta, cria o pedido da Inovação (se houve) e abre a sessão. Como o código
     * chegou na caixa de entrada, a confirmação vale como verificação deste navegador, que fica confiável.
     */
    @Transactional
    public ConfirmedAccount verify(EmailVerificationRequest request, ClientInfo client) {
        User user = userRepository.findByEmail(Emails.normalize(request.email()))
                .filter(User::isPendingVerification)
                .orElse(null);
        if (user == null || !codes.verify(user.getId(), CodePurpose.EMAIL_VERIFICATION, request.code())) {
            throw new InvalidVerificationCodeException();
        }
        user.confirmEmail(OffsetDateTime.now(clock));
        Department requested = user.takeRequestedDepartment();
        if (requested != null) {
            userService.createDepartmentRequest(user, requested);
        }
        securityEvents.record(SecurityEventType.EMAIL_VERIFIED, user, client, null);
        String deviceToken = trustedDevices.trust(user, client.userAgent());
        return new ConfirmedAccount(user, authService.startSession(user), deviceToken);
    }

    /** Sempre a mesma resposta; só cadastros pendentes recebem um código novo. */
    @Transactional(noRollbackFor = CodeRequestLimitException.class)
    public void resend(String email, ClientInfo client) {
        userRepository.findByEmail(Emails.normalize(email))
                .filter(User::isPendingVerification)
                .ifPresent(user -> {
                    try {
                        codes.send(user, CodePurpose.EMAIL_VERIFICATION, client.ip());
                    } catch (CodeRequestLimitException ignored) {
                        // Pedido cedo demais: o código anterior continua valendo; a resposta não muda
                    }
                });
    }

    /** Faxina diária (AccountMaintenanceJob). */
    @Transactional
    public int purgeExpired() {
        return userRepository.deleteByStatusCreatedBefore(UserStatus.PENDING_VERIFICATION,
                OffsetDateTime.now(clock).minus(PENDING_LIFETIME));
    }

    /** A conta confirmada, a sessão aberta e o navegador confiável (o controller grava os cookies). */
    public record ConfirmedAccount(User user, AuthenticatedSession session, String trustedDeviceToken) {
    }
}

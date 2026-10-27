package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TemporaryPasswordResponse;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.security.TemporaryPasswordGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Redefinição de senha pelo administrador (PLANO-SEGURANCA §6): é o único caminho de volta para uma
 * conta bloqueada. O sistema gera a senha temporária (o admin nunca escolhe nem vê a definitiva), que
 * vale 24 h e obriga a troca no primeiro acesso. Quem pode chamar: só ROLE_ADMIN (SecurityConfig).
 */
@Service
public class AdminPasswordResetService {

    static final Duration TEMPORARY_PASSWORD_VALIDITY = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator generator;
    private final RefreshTokenService refreshTokenService;
    private final SecurityEventService securityEvents;
    private final AccountEmails accountEmails;
    private final Clock clock;

    public AdminPasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                     TemporaryPasswordGenerator generator, RefreshTokenService refreshTokenService,
                                     SecurityEventService securityEvents, AccountEmails accountEmails, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.generator = generator;
        this.refreshTokenService = refreshTokenService;
        this.securityEvents = securityEvents;
        this.accountEmails = accountEmails;
        this.clock = clock;
    }

    @Transactional
    public TemporaryPasswordResponse reset(UUID userId, UUID adminId, ClientInfo client) {
        if (userId.equals(adminId)) {
            throw new InvalidAccountStatusException(
                    "Você não pode redefinir a sua própria senha por aqui. Use Meu perfil ou o \"Esqueci minha senha\".");
        }
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.BLOCKED) {
            throw new InvalidAccountStatusException("Só contas ativas ou bloqueadas têm a senha redefinida.");
        }
        User admin = userRepository.findById(adminId).orElseThrow(UserNotFoundException::new);

        String temporary = generator.generate();
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(TEMPORARY_PASSWORD_VALIDITY);
        user.resetPasswordByAdmin(passwordEncoder.encode(temporary), expiresAt);
        // Sessões e navegadores confiáveis caem: quem estava usando a conta perde o acesso na hora
        refreshTokenService.endAllSessions(user);
        securityEvents.record(SecurityEventType.PASSWORD_RESET_BY_ADMIN, user, client, "por " + adminId);
        accountEmails.passwordResetByAdmin(user, admin);
        return new TemporaryPasswordResponse(temporary, expiresAt);
    }
}

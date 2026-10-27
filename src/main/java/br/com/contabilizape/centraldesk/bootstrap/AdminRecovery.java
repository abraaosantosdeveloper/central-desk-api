package br.com.contabilizape.centraldesk.bootstrap;

import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.service.RefreshTokenService;
import br.com.contabilizape.centraldesk.service.SecurityEventService;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Recuperação de emergência (PLANO-SEGURANCA §6.4): quando todos os administradores ficam bloqueados
 * (ou desativados), ninguém consegue redefinir senha pelo sistema. Com ADMIN_RECOVERY_EMAIL e
 * ADMIN_PASSWORD nas variáveis do servidor, esta conta de administrador volta a ACTIVE na subida, com
 * ADMIN_PASSWORD como senha temporária (24 h, troca obrigatória no primeiro acesso).
 *
 * Só age sobre conta de administrador BLOQUEADA ou DESATIVADA: se a variável for esquecida no servidor,
 * as próximas subidas não redefinem a senha de novo. Mesmo assim, remova as duas variáveis depois de usar.
 */
@Component
@Order(1)
public class AdminRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminRecovery.class);
    static final Duration TEMPORARY_PASSWORD_VALIDITY = Duration.ofHours(24);

    private final String recoveryEmail;
    private final String password;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecurityEventService securityEvents;
    private final Validator validator;
    private final Clock clock;

    public AdminRecovery(@Value("${app.bootstrap.recovery-email:}") String recoveryEmail,
                         @Value("${app.bootstrap.admin.password:}") String password,
                         UserRepository userRepository, AdminRepository adminRepository,
                         PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                         SecurityEventService securityEvents, Validator validator, Clock clock) {
        this.recoveryEmail = recoveryEmail;
        this.password = password;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.securityEvents = securityEvents;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (recoveryEmail == null || recoveryEmail.isBlank()) {
            return;
        }
        if (password == null || password.isBlank()
                || !validator.validateValue(UserCreateRequest.class, "password", password).isEmpty()) {
            throw new IllegalStateException("ADMIN_RECOVERY_EMAIL definido, mas ADMIN_PASSWORD ausente ou fraca "
                    + "(8 a 72 caracteres, com letra e número).");
        }
        String email = recoveryEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email)
                .filter(found -> adminRepository.existsById(found.getId()))
                .orElseThrow(() -> new IllegalStateException(
                        "ADMIN_RECOVERY_EMAIL não corresponde a uma conta de administrador: " + email));

        if (user.getStatus() != UserStatus.BLOCKED && user.getStatus() != UserStatus.DEACTIVATED) {
            log.warn("ADMIN_RECOVERY_EMAIL definido, mas a conta {} está {}: nada foi feito. Remova a variável do servidor.",
                    email, user.getStatus());
            return;
        }
        if (user.getStatus() == UserStatus.DEACTIVATED) {
            user.reactivate();
        }
        user.resetPasswordByAdmin(passwordEncoder.encode(password), OffsetDateTime.now(clock).plus(TEMPORARY_PASSWORD_VALIDITY));
        refreshTokenService.endAllSessions(user);
        securityEvents.record(SecurityEventType.ADMIN_RECOVERY, user, null, "recuperação de emergência na subida");
        log.warn("Recuperação de emergência: a conta de administrador {} foi liberada com ADMIN_PASSWORD como senha "
                + "temporária (24 h, troca obrigatória). Remova ADMIN_RECOVERY_EMAIL e ADMIN_PASSWORD do servidor.", email);
    }
}

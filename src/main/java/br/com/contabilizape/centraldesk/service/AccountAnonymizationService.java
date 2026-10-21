package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.AccountNotDeactivatedException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.PasswordConfirmationMismatchException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.NotificationCleanupRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Encerramento de conta (RF04, LGPD): anonimiza o usuário sem exclusão física, para preservar
 * o histórico dos chamados. Tudo na mesma transação: sessões, chamados em atendimento,
 * notificações e o registro de administrador.
 */
@Service
public class AccountAnonymizationService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final NotificationCleanupRepository notificationCleanup;
    private final AccountOffboarding offboarding;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountAnonymizationService(UserRepository userRepository, AdminRepository adminRepository,
                                       NotificationCleanupRepository notificationCleanup, AccountOffboarding offboarding,
                                       PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.notificationCleanup = notificationCleanup;
        this.offboarding = offboarding;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** O próprio usuário encerra a conta, confirmando a senha. */
    @Transactional
    public void anonymizeOwnAccount(UUID userId, String password) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new PasswordConfirmationMismatchException();
        }
        anonymize(user);
    }

    /** O administrador anonimiza uma conta já desativada. */
    @Transactional
    public void anonymizeByAdmin(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new AccountNotDeactivatedException();
        }
        anonymize(user);
    }

    private void anonymize(User user) {
        UUID userId = user.getId();
        offboarding.ensureNotLastActiveAdmin(userId);
        if (adminRepository.existsById(userId)) {
            adminRepository.deleteById(userId);
        }

        // Hash de um valor aleatório e descartado: ninguém mais entra com esta conta
        user.anonymize(passwordEncoder.encode(UUID.randomUUID().toString()), OffsetDateTime.now(clock));

        offboarding.release(userId);
        notificationCleanup.deleteByRecipientId(userId);
    }
}

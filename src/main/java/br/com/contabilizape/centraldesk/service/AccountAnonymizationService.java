package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.AccountNotDeactivatedException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.exception.PasswordConfirmationMismatchException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.NotificationCleanupRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
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
    private final TicketRepository ticketRepository;
    private final NotificationCleanupRepository notificationCleanup;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountAnonymizationService(UserRepository userRepository, AdminRepository adminRepository,
                                       TicketRepository ticketRepository, NotificationCleanupRepository notificationCleanup,
                                       RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.ticketRepository = ticketRepository;
        this.notificationCleanup = notificationCleanup;
        this.refreshTokenService = refreshTokenService;
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
        removeAdminRecord(userId);

        // Hash de um valor aleatório e descartado: ninguém mais entra com esta conta
        user.anonymize(passwordEncoder.encode(UUID.randomUUID().toString()), OffsetDateTime.now(clock));

        refreshTokenService.revokeAll(userId);
        ticketRepository.findByAssigneeIdAndStatus(userId, TicketStatus.IN_PROGRESS)
                .forEach(Ticket::returnToQueue);
        notificationCleanup.deleteByRecipientId(userId);
    }

    private void removeAdminRecord(UUID userId) {
        if (!adminRepository.existsById(userId)) {
            return;
        }
        boolean anotherActiveAdmin = userRepository.findAdminsByStatus(UserStatus.ACTIVE).stream()
                .anyMatch(admin -> !admin.getId().equals(userId));
        if (!anotherActiveAdmin) {
            throw new LastAdminException();
        }
        adminRepository.deleteById(userId);
    }
}

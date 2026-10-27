package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TwoFactorStatusResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorUpdateRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CurrentPasswordMismatchException;
import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.InvalidVerificationCodeException;
import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seção "Segurança" de Meu perfil (PLANO-SEGURANCA §9.3): ligar e desligar a verificação em duas etapas.
 * Para administradores ela é obrigatória (S4); desligar pede a senha atual e um código (S6).
 */
@Service
public class TwoFactorSettingsService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codes;
    private final TrustedDeviceService trustedDevices;
    private final SecurityEventService securityEvents;
    private final AccountEmails accountEmails;

    public TwoFactorSettingsService(UserRepository userRepository, AdminRepository adminRepository,
                                    PasswordEncoder passwordEncoder, VerificationCodeService codes,
                                    TrustedDeviceService trustedDevices, SecurityEventService securityEvents,
                                    AccountEmails accountEmails) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.codes = codes;
        this.trustedDevices = trustedDevices;
        this.securityEvents = securityEvents;
        this.accountEmails = accountEmails;
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusResponse status(UUID userId) {
        User user = findActive(userId);
        boolean admin = adminRepository.existsById(userId);
        return new TwoFactorStatusResponse(admin || user.isTwoFactorEnabled(), admin);
    }

    /** Código para confirmar o desligamento. */
    @Transactional
    public void sendDisableCode(UUID userId, ClientInfo client) {
        User user = findActive(userId);
        ensureNotAdmin(userId);
        codes.send(user, CodePurpose.TFA_DISABLE, client.ip());
    }

    @Transactional
    public TwoFactorStatusResponse update(UUID userId, TwoFactorUpdateRequest request, ClientInfo client) {
        User user = findActive(userId);
        if (request.enabled()) {
            if (!user.isTwoFactorEnabled()) {
                user.enableTwoFactor();
                securityEvents.record(SecurityEventType.TWO_FACTOR_ENABLED, user, client, null);
            }
            return status(userId);
        }

        ensureNotAdmin(userId);
        if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        if (!codes.verify(userId, CodePurpose.TFA_DISABLE, request.code())) {
            throw new InvalidVerificationCodeException();
        }
        user.disableTwoFactor();
        // Sem a verificação, nenhum navegador precisa ficar marcado como confiável
        trustedDevices.revokeAll(userId);
        securityEvents.record(SecurityEventType.TWO_FACTOR_DISABLED, user, client, null);
        accountEmails.twoFactorDisabled(user);
        return status(userId);
    }

    private void ensureNotAdmin(UUID userId) {
        if (adminRepository.existsById(userId)) {
            throw new InvalidAccountStatusException("Para administradores, a verificação em duas etapas é obrigatória.");
        }
    }

    private User findActive(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}

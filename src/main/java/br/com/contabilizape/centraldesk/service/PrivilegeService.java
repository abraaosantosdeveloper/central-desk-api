package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.InvalidAccountStatusException;
import br.com.contabilizape.centraldesk.exception.UserNotFoundException;
import br.com.contabilizape.centraldesk.model.Admin;
import br.com.contabilizape.centraldesk.model.Coordinator;
import br.com.contabilizape.centraldesk.model.PrivilegeLevel;
import br.com.contabilizape.centraldesk.model.SecurityEventType;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.CoordinatorRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * "Alterar privilégios" em Usuários (PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §3, SCRUM-219): troca o nível
 * de uma conta entre ADMIN, COORDINATOR e COLLABORATOR, exclusivos entre si (PV1). Quem pode chamar é decidido
 * no SecurityConfig (só ROLE_ADMIN).
 */
@Service
public class PrivilegeService {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final CoordinatorRepository coordinatorRepository;
    private final AccountOffboarding offboarding;
    private final SecurityEventService securityEvents;
    private final AccountEmails accountEmails;
    private final UUID handlerDepartmentId;

    public PrivilegeService(UserRepository userRepository, AdminRepository adminRepository,
                            CoordinatorRepository coordinatorRepository, AccountOffboarding offboarding,
                            SecurityEventService securityEvents, AccountEmails accountEmails,
                            @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.coordinatorRepository = coordinatorRepository;
        this.offboarding = offboarding;
        this.securityEvents = securityEvents;
        this.accountEmails = accountEmails;
        this.handlerDepartmentId = handlerDepartmentId;
    }

    /** Nível atual de uma conta, lido do banco. */
    @Transactional(readOnly = true)
    public PrivilegeLevel levelOf(UUID userId) {
        if (adminRepository.existsById(userId)) {
            return PrivilegeLevel.ADMIN;
        }
        return coordinatorRepository.existsById(userId) ? PrivilegeLevel.COORDINATOR : PrivilegeLevel.COLLABORATOR;
    }

    /**
     * Troca o nível de privilégio. Regras: ninguém altera o próprio e a conta precisa estar ativa (PV4);
     * ADMIN só na Inovação (PV2); sempre sobra um administrador ativo (PV3). Pedir o nível que a conta já tem
     * não muda nada. A mudança vale na hora (PV6): as sessões da pessoa caem e os navegadores dela recebem
     * "session.ended"; fica na auditoria e a pessoa recebe um e-mail (PV7).
     *
     * @return o nível anterior
     */
    @Transactional
    public PrivilegeLevel change(UUID userId, PrivilegeLevel level, UUID adminId, ClientInfo client) {
        if (userId.equals(adminId)) {
            throw new InvalidAccountStatusException("Você não pode alterar o seu próprio privilégio.");
        }
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAccountStatusException("Só contas ativas podem ter o privilégio alterado.");
        }
        PrivilegeLevel previous = levelOf(userId);
        if (previous == level) {
            return previous;
        }
        if (level == PrivilegeLevel.ADMIN && !user.getDepartment().getId().equals(handlerDepartmentId)) {
            throw new InvalidAccountStatusException(
                    "Somente contas da Inovação podem ser administradoras. Mude o departamento da pessoa primeiro.");
        }
        User admin = userRepository.findById(adminId).orElseThrow(UserNotFoundException::new);

        if (previous == PrivilegeLevel.ADMIN) {
            offboarding.ensureNotLastActiveAdmin(userId);
            adminRepository.deleteById(userId);
        } else if (previous == PrivilegeLevel.COORDINATOR) {
            coordinatorRepository.deleteById(userId);
        }
        // A remoção vai para o banco antes da inclusão: a mesma conta nunca fica nas duas tabelas
        adminRepository.flush();
        coordinatorRepository.flush();
        switch (level) {
            case ADMIN -> adminRepository.save(new Admin(user, admin));
            case COORDINATOR -> coordinatorRepository.save(new Coordinator(user, admin));
            case COLLABORATOR -> { }
        }

        offboarding.endSessions(user);
        securityEvents.record(SecurityEventType.PRIVILEGE_CHANGED, user, client,
                previous + " -> " + level + " por " + adminId);
        accountEmails.privilegeChanged(user, admin, previous, level);
        return previous;
    }
}

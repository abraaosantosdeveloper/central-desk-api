package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * O que acontece quando alguém sai do sistema, seja desativado pelo admin ou anonimizado:
 * as sessões caem na hora, os chamados em atendimento voltam para a fila e o sistema
 * nunca fica sem um administrador ativo. Roda dentro da transação de quem chama.
 */
@Component
class AccountOffboarding {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final TicketRepository ticketRepository;
    private final RefreshTokenService refreshTokenService;

    AccountOffboarding(UserRepository userRepository, AdminRepository adminRepository,
                       TicketRepository ticketRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.ticketRepository = ticketRepository;
        this.refreshTokenService = refreshTokenService;
    }

    /** Recusa a saída do último administrador ativo; a regra vale para desativar e anonimizar. */
    void ensureNotLastActiveAdmin(UUID userId) {
        if (!adminRepository.existsById(userId)) {
            return;
        }
        boolean anotherActiveAdmin = userRepository.findAdminsByStatus(UserStatus.ACTIVE).stream()
                .anyMatch(admin -> !admin.getId().equals(userId));
        if (!anotherActiveAdmin) {
            throw new LastAdminException();
        }
    }

    /** Derruba as sessões e devolve à fila os chamados que estavam com a pessoa. */
    void release(UUID userId) {
        refreshTokenService.revokeAll(userId);
        ticketRepository.findByAssigneeIdAndStatus(userId, TicketStatus.IN_PROGRESS)
                .forEach(Ticket::returnToQueue);
    }
}

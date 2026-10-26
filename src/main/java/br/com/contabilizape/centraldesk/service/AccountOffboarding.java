package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.event.TicketsReturnedToQueueEvent;
import br.com.contabilizape.centraldesk.exception.LastAdminException;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
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
    private final ApplicationEventPublisher eventPublisher;

    AccountOffboarding(UserRepository userRepository, AdminRepository adminRepository,
                       TicketRepository ticketRepository, RefreshTokenService refreshTokenService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.ticketRepository = ticketRepository;
        this.refreshTokenService = refreshTokenService;
        this.eventPublisher = eventPublisher;
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

    /** Só derruba as sessões, na hora (ex.: o e-mail de login mudou). */
    void endSessions(User user) {
        refreshTokenService.endAllSessions(user);
    }

    /** Derruba as sessões e devolve à fila os chamados que estavam com a pessoa. */
    void release(UUID userId) {
        refreshTokenService.revokeAll(userId);
        returnTicketsToQueue(userId);
    }

    /**
     * Devolve à fila os chamados em atendimento da pessoa e avisa uma vez só, se houve algum:
     * quem está com a fila aberta passa a ver todos eles.
     */
    void returnTicketsToQueue(UUID userId) {
        List<Ticket> tickets = ticketRepository.findByAssigneeIdAndStatus(userId, TicketStatus.IN_PROGRESS);
        if (tickets.isEmpty()) {
            return;
        }
        tickets.forEach(Ticket::returnToQueue);
        eventPublisher.publishEvent(TicketsReturnedToQueueEvent.of(tickets));
    }
}

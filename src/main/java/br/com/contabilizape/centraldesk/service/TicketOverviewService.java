package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.OverviewAssigneeResponse;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter.OverviewOrder;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter.SectorRelation;
import br.com.contabilizape.centraldesk.dto.TicketOverviewResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.TicketOverviewForbiddenException;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.repository.AdminRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tela "Todos os chamados" (PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §2): administrador vê todos os chamados
 * (TC1); coordenador, os que o próprio setor abriu ou recebeu (TC2); os demais recebem 403 (TC4). Abertos, em
 * andamento e encerrados vêm juntos; o status é só um filtro, com contadores por aba (TC3).
 *
 * As condições de administrador e de coordenador, e o setor do coordenador, vêm do banco, nunca do token
 * nem da requisição: o cliente não amplia o próprio escopo.
 */
@Service
public class TicketOverviewService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Sort MOST_URGENT_FIRST = Sort.by(Sort.Order.desc("priority.id"), Sort.Order.asc("createdAt"));
    private static final Sort LAST_UPDATED_FIRST = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"));

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final ZoneId zone;

    public TicketOverviewService(TicketRepository ticketRepository, UserRepository userRepository,
                                 AdminRepository adminRepository,
                                 @Value("${app.tickets.time-zone}") ZoneId zone) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public TicketOverviewResponse overview(UUID userId, TicketOverviewFilter filter, Pageable pageable) {
        User user = findActiveUser(userId);
        Specification<Ticket> filtered = Specification.allOf(scopeOf(user, filter),
                TicketSpecifications.matchingOverview(filter, zone));
        Specification<Ticket> listed = filter.status() == null
                ? filtered
                : filtered.and(TicketSpecifications.hasStatus(filter.status()));

        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                orderOf(filter.orderBy()));
        Page<Ticket> tickets = ticketRepository.findAll(listed, page);

        // Contadores das abas: os mesmos filtros, menos o de status
        long open = ticketRepository.count(filtered.and(TicketSpecifications.hasStatus(TicketStatus.OPEN)));
        long inProgress = ticketRepository.count(filtered.and(TicketSpecifications.hasStatus(TicketStatus.IN_PROGRESS)));
        long closed = ticketRepository.count(filtered.and(TicketSpecifications.hasStatus(TicketStatus.CLOSED)));

        return new TicketOverviewResponse(
                tickets.getContent().stream().map(TicketSummaryResponse::from).toList(),
                tickets.getNumber(),
                tickets.getSize(),
                tickets.getTotalElements(),
                tickets.getTotalPages(),
                new TicketOverviewResponse.StatusCounts(open + inProgress + closed, open, inProgress, closed));
    }

    /**
     * Opções do filtro de responsável: administrador, quem atende qualquer chamado; coordenador, só quem atende
     * chamados do próprio setor (origem ou destino). Demais perfis: 403.
     */
    @Transactional(readOnly = true)
    public List<OverviewAssigneeResponse> assignees(UUID userId) {
        User user = findActiveUser(userId);
        if (adminRepository.existsById(user.getId())) {
            return ticketRepository.findAllAssignees();
        }
        if (userRepository.isCoordinator(user.getId())) {
            return ticketRepository.findAssigneesInSector(user.getDepartment().getId());
        }
        throw new TicketOverviewForbiddenException();
    }

    /**
     * Administrador: tudo, com os filtros opcionais de origem e destino. Coordenador: o setor dele (o
     * departamento atual da conta), como origem, destino ou os dois; origem e destino pedidos são ignorados.
     */
    private Specification<Ticket> scopeOf(User user, TicketOverviewFilter filter) {
        if (adminRepository.existsById(user.getId())) {
            List<Specification<Ticket>> scope = new ArrayList<>();
            if (filter.originDepartmentId() != null) {
                scope.add(TicketSpecifications.originIs(filter.originDepartmentId()));
            }
            if (filter.targetDepartmentId() != null) {
                scope.add(TicketSpecifications.targetIs(filter.targetDepartmentId()));
            }
            return Specification.allOf(scope);
        }
        if (userRepository.isCoordinator(user.getId())) {
            UUID sector = user.getDepartment().getId();
            SectorRelation relation = filter.relation() == null ? SectorRelation.BOTH : filter.relation();
            return switch (relation) {
                case ORIGIN -> TicketSpecifications.originIs(sector);
                case TARGET -> TicketSpecifications.targetIs(sector);
                case BOTH -> TicketSpecifications.originIs(sector).or(TicketSpecifications.targetIs(sector));
            };
        }
        throw new TicketOverviewForbiddenException();
    }

    private static Sort orderOf(OverviewOrder order) {
        if (order == null) {
            return NEWEST_FIRST;
        }
        return switch (order) {
            case RECENT -> NEWEST_FIRST;
            case PRIORITY -> MOST_URGENT_FIRST;
            case UPDATED -> LAST_UPDATED_FIRST;
        };
    }

    // O token vale até 15 min: a conta pode ter sido desativada nesse meio-tempo
    private User findActiveUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(InvalidSessionException::new);
        if (!user.isActive()) {
            throw new AccountNotActiveException();
        }
        return user;
    }
}

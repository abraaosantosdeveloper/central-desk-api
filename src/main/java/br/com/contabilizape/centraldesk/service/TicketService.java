package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.TicketCreateRequest;
import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketResponse;
import br.com.contabilizape.centraldesk.dto.TicketSummaryResponse;
import br.com.contabilizape.centraldesk.dto.TicketUpdateRequest;
import br.com.contabilizape.centraldesk.exception.AccountNotActiveException;
import br.com.contabilizape.centraldesk.exception.CategoryNotFoundException;
import br.com.contabilizape.centraldesk.exception.InvalidSessionException;
import br.com.contabilizape.centraldesk.exception.PriorityNotFoundException;
import br.com.contabilizape.centraldesk.exception.SelfAssignmentException;
import br.com.contabilizape.centraldesk.exception.TicketEditForbiddenException;
import br.com.contabilizape.centraldesk.exception.TicketNotEditableException;
import br.com.contabilizape.centraldesk.exception.TicketNotFoundException;
import br.com.contabilizape.centraldesk.model.Category;
import br.com.contabilizape.centraldesk.model.Priority;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.CategoryRepository;
import br.com.contabilizape.centraldesk.repository.PriorityRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import br.com.contabilizape.centraldesk.security.Roles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PriorityRepository priorityRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository,
                         UserRepository userRepository,
                         CategoryRepository categoryRepository,
                         PriorityRepository priorityRepository,
                         Clock clock) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.priorityRepository = priorityRepository;
        this.clock = clock;
    }

    /**
     * Encerra o chamado. Podem: o solicitante, a Inovação e os administradores (as mesmas pessoas que o veem).
     * Os demais recebem 404, como em GET /tickets/{id}.
     */
    @Transactional
    public TicketResponse close(UUID ticketId, UUID userId, List<String> roles) {
        User closer = findActiveRequester(userId);
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .filter(found -> canView(found, userId, roles))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        ticket.close(closer, OffsetDateTime.now(clock));
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        return TicketResponse.from(ticket);
    }

    /**
     * O usuário logado (Inovação ou admin, regra no SecurityConfig) assume o chamado: OPEN → IN_PROGRESS.
     * A linha fica travada até o fim da transação; se duas pessoas assumirem ao mesmo tempo,
     * a segunda encontra IN_PROGRESS e recebe 409.
     */
    @Transactional
    public TicketResponse assign(UUID ticketId, UUID userId) {
        User assignee = findActiveRequester(userId);
        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.getRequester().getId().equals(userId)) {
            throw new SelfAssignmentException();
        }
        ticket.assign(assignee, OffsetDateTime.now(clock));
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        return TicketResponse.from(ticket);
    }

    /**
     * Abre um chamado em nome do usuário logado (requesterId vem do token, nunca do corpo).
     */
    @Transactional
    public TicketResponse open(UUID requesterId, TicketCreateRequest request) {
        User requester = findActiveRequester(requesterId);
        Category category = findCategory(request.categoryId());
        Priority priority = findPriority(request.priorityId());

        Ticket ticket = new Ticket(request.title().trim(), request.description().trim(), category, priority, requester);
        ticketRepository.saveAndFlush(ticket); // flush preenche o createdAt antes de montar a resposta
        return TicketResponse.from(ticket);
    }

    /**
     * Edita o conteúdo do chamado. Regras: só o solicitante, e só enquanto ninguém da Inovação assumiu (OPEN).
     */
    @Transactional
    public TicketResponse edit(UUID ticketId, UUID userId, TicketUpdateRequest request) {
        findActiveRequester(userId);
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (!ticket.getRequester().getId().equals(userId)) {
            throw new TicketEditForbiddenException();
        }
        if (!ticket.isOpen()) {
            throw new TicketNotEditableException();
        }

        ticket.edit(request.title().trim(), request.description().trim(),
                findCategory(request.categoryId()), findPriority(request.priorityId()));
        ticketRepository.flush(); // grava agora e atualiza o updated_at
        return TicketResponse.from(ticket);
    }

    /**
     * Detalhes do chamado. Veem: o solicitante, a Inovação (TICKET_HANDLER) e os administradores.
     * Para os demais, 404: quem não pode ver não descobre que o chamado existe.
     */
    @Transactional(readOnly = true)
    public TicketResponse findById(UUID ticketId, UUID userId, List<String> roles) {
        findActiveRequester(userId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(found -> canView(found, userId, roles))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return TicketResponse.from(ticket);
    }

    public static final int MAX_PAGE_SIZE = 100;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    /** Painel do colaborador: os chamados que ele abriu. */
    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryResponse> listMine(UUID userId, TicketFilter filter, Pageable pageable) {
        findActiveRequester(userId);
        return list(TicketSpecifications.requestedBy(userId), filter, pageable);
    }

    /** Fila da Inovação: chamados OPEN, ainda sem responsável. Quem pode ver é decidido no SecurityConfig. */
    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryResponse> listQueue(UUID userId, TicketFilter filter, Pageable pageable) {
        findActiveRequester(userId);
        return list(TicketSpecifications.inQueue(), filter, pageable);
    }

    /** Chamados sob responsabilidade do usuário logado, em atendimento ou já encerrados. */
    @Transactional(readOnly = true)
    public PageResponse<TicketSummaryResponse> listAssigned(UUID userId, TicketFilter filter, Pageable pageable) {
        findActiveRequester(userId);
        return list(TicketSpecifications.assignedTo(userId), filter, pageable);
    }

    // A ordem é sempre "mais recentes primeiro"; a ordenação pedida pelo cliente é ignorada
    private PageResponse<TicketSummaryResponse> list(Specification<Ticket> scope, TicketFilter filter, Pageable pageable) {
        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
        Specification<Ticket> spec = Specification.allOf(scope, TicketSpecifications.matching(filter));
        return PageResponse.from(ticketRepository.findAll(spec, page), TicketSummaryResponse::from);
    }

    private static boolean canView(Ticket ticket, UUID userId, List<String> roles) {
        return ticket.getRequester().getId().equals(userId)
                || roles.contains(Roles.TICKET_HANDLER)
                || roles.contains(Roles.ADMIN);
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Priority findPriority(Short priorityId) {
        return priorityRepository.findById(priorityId).orElseThrow(() -> new PriorityNotFoundException(priorityId));
    }

    // O token vale até 15 min: a conta pode ter sido desativada nesse meio-tempo
    private User findActiveRequester(UUID requesterId) {
        User requester = userRepository.findById(requesterId).orElseThrow(InvalidSessionException::new);
        if (requester.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        return requester;
    }
}

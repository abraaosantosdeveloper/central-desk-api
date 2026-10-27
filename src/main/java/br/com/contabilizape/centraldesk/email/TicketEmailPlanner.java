package br.com.contabilizape.centraldesk.email;

import br.com.contabilizape.centraldesk.event.TicketAssignedEvent;
import br.com.contabilizape.centraldesk.event.TicketClosedEvent;
import br.com.contabilizape.centraldesk.event.TicketCommentedEvent;
import br.com.contabilizape.centraldesk.event.TicketOpenedEvent;
import br.com.contabilizape.centraldesk.event.TicketReassignedEvent;
import br.com.contabilizape.centraldesk.event.TicketReopenedEvent;
import br.com.contabilizape.centraldesk.model.Comment;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketClosure;
import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.CommentRepository;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import br.com.contabilizape.centraldesk.repository.TicketClosureRepository;
import br.com.contabilizape.centraldesk.repository.TicketRepository;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quem recebe e-mail de cada acontecimento do chamado (PLANO-SEGURANCA-CONTAS-E-EMAILS.md §13.1, que
 * substitui a matriz do ARQUITETURA-EMAILS.md §2). Regras gerais:
 * <ul>
 *   <li>só contas ACTIVE recebem;</li>
 *   <li>um e-mail por pessoa: quem ocupa dois papéis recebe o do mais específico;</li>
 *   <li>quem fez a ação não recebe, exceto na reatribuição, quando é o responsável anterior ou o novo.</li>
 * </ul>
 * Cada método relê o chamado numa transação própria, só de leitura (roda depois do commit da ação).
 */
@Component
public class TicketEmailPlanner {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final DepartmentRepository departmentRepository;
    private final TicketClosureRepository closureRepository;
    private final EmailRenderer renderer;
    private final EmailFormats formats;
    private final Clock clock;
    private final UUID handlerDepartmentId;

    public TicketEmailPlanner(TicketRepository ticketRepository, UserRepository userRepository,
                              CommentRepository commentRepository, DepartmentRepository departmentRepository,
                              TicketClosureRepository closureRepository, EmailRenderer renderer, EmailFormats formats, Clock clock,
                              @Value("${app.tickets.handler-department-id}") UUID handlerDepartmentId) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.departmentRepository = departmentRepository;
        this.closureRepository = closureRepository;
        this.renderer = renderer;
        this.formats = formats;
        this.clock = clock;
        this.handlerDepartmentId = handlerDepartmentId;
    }

    /** Abrir: o solicitante e cada admin ativo do setor de destino (o solicitante admin recebe só a versão dele). */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketOpenedEvent event) {
        return withTicket(event.ticketId(), ticket -> {
            Recipients recipients = new Recipients();
            recipients.add(ticket.getRequester(), EmailTemplate.TICKET_OPENED_REQUESTER, Map.of());
            for (User admin : userRepository.findAdminsInDepartment(handlerDepartmentId, UserStatus.ACTIVE)) {
                recipients.add(admin, EmailTemplate.TICKET_OPENED_DEPARTMENT, Map.of());
            }
            return compose(ticket, event.eventId(), recipients);
        });
    }

    /** Assumir: o solicitante (nada, se foi ele mesmo — admin com a própria demanda). */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketAssignedEvent event) {
        return withTicket(event.ticketId(), ticket -> {
            Recipients recipients = new Recipients();
            if (!event.requesterId().equals(event.assigneeId()) && ticket.getAssignee() != null) {
                OffsetDateTime assignedAt = ticket.getAssignedAt() != null ? ticket.getAssignedAt() : now();
                recipients.add(ticket.getRequester(), EmailTemplate.TICKET_IN_PROGRESS, Map.of(
                        "assigneeName", ticket.getAssignee().getFullName(),
                        "assignedDate", formats.date(assignedAt),
                        "assignedTime", formats.time(assignedAt)));
            }
            return compose(ticket, event.eventId(), recipients);
        });
    }

    /**
     * Reatribuir: o novo responsável, o anterior (se havia) e o solicitante. Quem reatribuiu não recebe,
     * a menos que seja o anterior ou o novo responsável. Um e-mail por pessoa: novo > anterior > solicitante.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketReassignedEvent event) {
        return withTicket(event.ticketId(), ticket -> {
            User newAssignee = userRepository.findById(event.newAssigneeId()).orElse(null);
            User previous = event.previousAssigneeId() == null ? null
                    : userRepository.findById(event.previousAssigneeId()).orElse(null);
            String reassignedBy = userRepository.findById(event.actorId()).map(User::getFullName).orElse("Um administrador");
            OffsetDateTime now = now();
            Map<String, Object> common = Map.of(
                    "reassignedByName", reassignedBy,
                    "reassignedDate", formats.date(now),
                    "reassignedTime", formats.time(now),
                    "newAssigneeName", newAssignee == null ? "" : newAssignee.getFullName());

            Recipients recipients = new Recipients();
            recipients.add(newAssignee, EmailTemplate.TICKET_REASSIGNED_NEW_ASSIGNEE, common);
            recipients.add(previous, EmailTemplate.TICKET_REASSIGNED_PREVIOUS_ASSIGNEE, common);
            if (!ticket.getRequester().getId().equals(event.actorId())) {
                recipients.add(ticket.getRequester(), EmailTemplate.TICKET_REASSIGNED_REQUESTER, common);
            }
            // Quem reatribuiu só recebe no papel de responsável (anterior ou novo)
            recipients.removeUnlessRole(event.actorId(),
                    EmailTemplate.TICKET_REASSIGNED_NEW_ASSIGNEE, EmailTemplate.TICKET_REASSIGNED_PREVIOUS_ASSIGNEE);
            return compose(ticket, event.eventId(), recipients);
        });
    }

    /** Comentário: só o outro lado, decidido no momento do comentário. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketCommentedEvent event) {
        if (event.recipientId() == null) {
            return List.of();
        }
        return withTicket(event.ticketId(), ticket -> {
            Comment comment = commentRepository.findById(event.commentId()).orElse(null);
            User recipient = userRepository.findById(event.recipientId()).orElse(null);
            if (comment == null || recipient == null) {
                return List.<EmailMessage>of();
            }
            boolean authorIsRequester = comment.getAuthor().getId().equals(ticket.getRequester().getId());
            EmailFormats.Excerpt excerpt = formats.excerpt(comment.getContent());
            OffsetDateTime sentAt = comment.getCreatedAt() != null ? comment.getCreatedAt() : now();
            Recipients recipients = new Recipients();
            recipients.add(recipient, EmailTemplate.TICKET_NEW_MESSAGE, Map.of(
                    "authorName", comment.getAuthor().getFullName(),
                    "authorRole", authorIsRequester ? "solicitante" : "responsável",
                    "messageLines", excerpt.lines(),
                    "messageTruncated", excerpt.truncated(),
                    "sentDate", formats.date(sentAt),
                    "sentTime", formats.time(sentAt)));
            return compose(ticket, event.eventId(), recipients);
        });
    }

    /** Encerrar: o solicitante e o responsável, menos quem encerrou. */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketClosedEvent event) {
        return withTicket(event.ticketId(), ticket -> {
            OffsetDateTime closedAt = ticket.getClosedAt() != null ? ticket.getClosedAt() : now();
            Map<String, Object> variables = Map.of(
                    "closedByName", ticket.getClosedBy() == null ? "" : ticket.getClosedBy().getFullName(),
                    "closedDate", formats.date(closedAt),
                    "closedTime", formats.time(closedAt),
                    "resolutionLines", formats.lines(ticket.getResolution()));
            Recipients recipients = new Recipients();
            recipients.add(ticket.getRequester(), EmailTemplate.TICKET_CLOSED, variables);
            recipients.add(ticket.getAssignee(), EmailTemplate.TICKET_CLOSED, variables);
            recipients.remove(event.closedById());
            return compose(ticket, event.eventId(), recipients);
        });
    }

    /**
     * Reabrir: quem volta a atender (o responsável, ou os admins do setor se voltou para a fila) e, se quem
     * reabriu não foi o solicitante, também o solicitante. Quem reabriu não recebe.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<EmailMessage> plan(TicketReopenedEvent event) {
        return withTicket(event.ticketId(), ticket -> {
            TicketClosure closure = closureRepository.findById(event.closureId()).orElse(null);
            if (closure == null) {
                return List.<EmailMessage>of();
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put("reopenedByName", closure.getReopenedBy().getFullName());
            variables.put("reopenedDate", formats.date(closure.getReopenedAt()));
            variables.put("reopenedTime", formats.time(closure.getReopenedAt()));
            variables.put("reasonLines", formats.lines(closure.getReopenReason()));
            variables.put("backToQueue", event.backToQueue());
            variables.put("assigneeName", ticket.getAssignee() == null ? null : ticket.getAssignee().getFullName());

            Recipients recipients = new Recipients();
            if (event.backToQueue()) {
                for (User admin : userRepository.findAdminsInDepartment(handlerDepartmentId, UserStatus.ACTIVE)) {
                    recipients.add(admin, EmailTemplate.TICKET_REOPENED, variables);
                }
            } else {
                recipients.add(ticket.getAssignee(), EmailTemplate.TICKET_REOPENED, variables);
            }
            if (!ticket.getRequester().getId().equals(event.actorId())) {
                recipients.add(ticket.getRequester(), EmailTemplate.TICKET_REOPENED, variables);
            }
            recipients.remove(event.actorId());
            return compose(ticket, event.eventId(), recipients);
        });
    }

    // ---------- montagem ----------

    private interface TicketPlan {
        List<EmailMessage> apply(Ticket ticket);
    }

    // Chamado apagado entre o commit e aqui: nada a fazer
    private List<EmailMessage> withTicket(UUID ticketId, TicketPlan plan) {
        return ticketRepository.findForEmail(ticketId).map(plan::apply).orElse(List.of());
    }

    private List<EmailMessage> compose(Ticket ticket, UUID eventId, Recipients recipients) {
        Map<String, Object> header = header(ticket);
        List<EmailMessage> messages = new ArrayList<>();
        recipients.entries.values().forEach(entry -> {
            Map<String, Object> variables = new HashMap<>(header);
            variables.putAll(entry.variables());
            variables.put("recipientName", entry.user().getFullName());
            messages.add(renderer.compose(entry.template(), entry.user().getEmail(), variables,
                    entry.template().file() + "/" + eventId + "/" + entry.user().getId(), ticket.getNumber()));
        });
        return messages;
    }

    private Map<String, Object> header(Ticket ticket) {
        OffsetDateTime openedAt = ticket.getCreatedAt() != null ? ticket.getCreatedAt() : now();
        Map<String, Object> header = new HashMap<>();
        header.put("ticketNumber", ticket.getNumber());
        header.put("ticketTitle", ticket.getTitle());
        header.put("ticketUrl", renderer.appUrl() + "/chamados/" + ticket.getId());
        header.put("categoryName", ticket.getCategory().getLabel());
        header.put("priorityName", ticket.getPriority().getLabel());
        header.put("requesterName", ticket.getRequester().getFullName());
        header.put("departmentName", departmentRepository.findById(handlerDepartmentId).map(Department::getName).orElse("Inovação"));
        header.put("openedDate", formats.date(openedAt));
        header.put("openedTime", formats.time(openedAt));
        return header;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /** Destinatários sem repetição: o primeiro papel registrado para a pessoa vale (ordem = prioridade). */
    private static final class Recipients {

        private record Entry(User user, EmailTemplate template, Map<String, Object> variables) {
        }

        private final Map<UUID, Entry> entries = new LinkedHashMap<>();

        void add(User user, EmailTemplate template, Map<String, Object> variables) {
            if (user != null && user.isActive()) {
                entries.putIfAbsent(user.getId(), new Entry(user, template, variables));
            }
        }

        void remove(UUID userId) {
            entries.remove(userId);
        }

        void removeUnlessRole(UUID userId, EmailTemplate... allowed) {
            Entry entry = entries.get(userId);
            if (entry != null && !List.of(allowed).contains(entry.template())) {
                entries.remove(userId);
            }
        }
    }
}

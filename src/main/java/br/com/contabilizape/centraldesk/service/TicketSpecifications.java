package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Critérios das listagens de chamados. Cada painel tem o seu recorte; os filtros opcionais
 * (status, categoria, prioridade) são somados a ele.
 */
final class TicketSpecifications {

    private TicketSpecifications() {
    }

    static Specification<Ticket> requestedBy(UUID userId) {
        return (ticket, query, cb) -> cb.equal(ticket.get("requester").get("id"), userId);
    }

    static Specification<Ticket> assignedTo(UUID userId) {
        return (ticket, query, cb) -> cb.equal(ticket.get("assignee").get("id"), userId);
    }

    static Specification<Ticket> inQueue() {
        return (ticket, query, cb) -> cb.and(
                cb.equal(ticket.get("status"), TicketStatus.OPEN),
                cb.isNull(ticket.get("assignee")));
    }

    static Specification<Ticket> matching(TicketFilter filter) {
        return (ticket, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(ticket.get("status"), filter.status()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(ticket.get("category").get("id"), filter.categoryId()));
            }
            if (filter.priorityId() != null) {
                predicates.add(cb.equal(ticket.get("priority").get("id"), filter.priorityId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

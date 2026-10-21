package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Critérios das listagens de chamados. Cada painel tem o seu recorte; os filtros opcionais
 * (status, categoria, prioridade, busca e departamento de quem abriu) são somados a ele.
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
            if (filter.departmentId() != null) {
                predicates.add(cb.equal(ticket.get("requester").get("department").get("id"), filter.departmentId()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String term = filter.q().trim().toLowerCase(Locale.ROOT);
                // O número exibido é o início do UUID (#1A2B3C4D)
                String number = term.startsWith("#") ? term.substring(1) : term;
                predicates.add(cb.or(
                        cb.like(cb.lower(ticket.get("title")), "%" + term + "%"),
                        cb.like(cb.function("text", String.class, ticket.get("id")), number + "%")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.TicketFilter;
import br.com.contabilizape.centraldesk.dto.TicketOverviewFilter;
import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Critérios das listagens de chamados. Cada painel tem o seu recorte; os filtros opcionais
 * (status, categoria, prioridade, busca e setor de origem) são somados a ele.
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

    // ---------- "Todos os chamados" (GET /tickets/overview) ----------

    static Specification<Ticket> originIs(UUID departmentId) {
        return (ticket, query, cb) -> cb.equal(ticket.get("originDepartment").get("id"), departmentId);
    }

    static Specification<Ticket> targetIs(UUID departmentId) {
        return (ticket, query, cb) -> cb.equal(ticket.get("targetDepartment").get("id"), departmentId);
    }

    static Specification<Ticket> hasStatus(TicketStatus status) {
        return (ticket, query, cb) -> cb.equal(ticket.get("status"), status);
    }

    /**
     * Filtros comuns da tela, menos o status (que separa as abas) e o escopo (que depende de quem pede).
     * Datas: dias inteiros no fuso da empresa, com {@code to} inclusive.
     */
    static Specification<Ticket> matchingOverview(TicketOverviewFilter filter, ZoneId zone) {
        return (ticket, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(ticket.get("category").get("id"), filter.categoryId()));
            }
            if (filter.priorityId() != null) {
                predicates.add(cb.equal(ticket.get("priority").get("id"), filter.priorityId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(ticket.<OffsetDateTime>get("createdAt"), startOf(filter.from(), zone)));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(ticket.<OffsetDateTime>get("createdAt"), startOf(filter.to().plusDays(1), zone)));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String term = filter.q().trim().toLowerCase(Locale.ROOT);
                String number = term.startsWith("#") ? term.substring(1) : term;
                boolean numeric = !number.isEmpty() && number.length() <= 18 && number.chars().allMatch(Character::isDigit);
                if (term.startsWith("#") && numeric) {
                    // "#123" é o número exibido na tela: só ele, sem misturar com o início de outros ids
                    predicates.add(cb.equal(ticket.get("number"), Long.parseLong(number)));
                } else {
                    List<Predicate> any = new ArrayList<>();
                    any.add(cb.like(cb.lower(ticket.get("title")), "%" + term + "%"));
                    any.add(cb.like(cb.lower(ticket.get("requester").get("fullName")), "%" + term + "%"));
                    any.add(cb.like(cb.function("text", String.class, ticket.get("id")), number + "%"));
                    if (numeric) {
                        any.add(cb.equal(ticket.get("number"), Long.parseLong(number)));
                    }
                    predicates.add(cb.or(any.toArray(Predicate[]::new)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static OffsetDateTime startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toOffsetDateTime();
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
                // Setor de origem gravado no chamado (SCRUM-213): não muda se a pessoa trocar de departamento
                predicates.add(cb.equal(ticket.get("originDepartment").get("id"), filter.departmentId()));
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

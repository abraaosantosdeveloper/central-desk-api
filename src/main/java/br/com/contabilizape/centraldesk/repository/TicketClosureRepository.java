package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.TicketClosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketClosureRepository extends JpaRepository<TicketClosure, UUID> {

    long countByTicketId(UUID ticketId);
}

package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
}

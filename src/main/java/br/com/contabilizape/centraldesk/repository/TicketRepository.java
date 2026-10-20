package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Ticket;
import br.com.contabilizape.centraldesk.model.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByAssigneeIdAndStatus(UUID assigneeId, TicketStatus status);

    /** Listagens: carrega junto o que o resumo mostra, sem uma consulta por chamado. */
    /** Lê o chamado travando a linha até o fim da transação: duas pessoas não assumem o mesmo chamado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"category", "priority", "requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);
}

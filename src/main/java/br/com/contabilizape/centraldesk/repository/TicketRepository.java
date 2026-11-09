package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.dto.OverviewAssigneeResponse;
import br.com.contabilizape.centraldesk.dto.TicketExportRow;
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

    /** Tamanho da fila: OPEN ⇔ sem responsável (a máquina de estados garante). */
    long countByStatus(TicketStatus status);

    /** Filtro de responsável (administrador): quem já é responsável por algum chamado, em qualquer setor. */
    @Query("""
            select distinct new br.com.contabilizape.centraldesk.dto.OverviewAssigneeResponse(a.id, a.fullName, a.department.name)
            from Ticket t join t.assignee a
            order by a.fullName
            """)
    List<OverviewAssigneeResponse> findAllAssignees();

    /** Filtro de responsável (coordenador): só quem atende chamados que o setor abriu ou recebeu. */
    @Query("""
            select distinct new br.com.contabilizape.centraldesk.dto.OverviewAssigneeResponse(a.id, a.fullName, a.department.name)
            from Ticket t join t.assignee a
            where t.originDepartment.id = :sector or t.targetDepartment.id = :sector
            order by a.fullName
            """)
    List<OverviewAssigneeResponse> findAssigneesInSector(@Param("sector") UUID sector);

    /** Listagens: carrega junto o que o resumo mostra, sem uma consulta por chamado. */
    /** Lê o chamado travando a linha até o fim da transação: duas pessoas não assumem o mesmo chamado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);

    /** E-mails: tudo o que os templates mostram, numa consulta. */
    @EntityGraph(attributePaths = {"category", "priority", "requester", "assignee", "closedBy"})
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findForEmail(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = {"category", "priority", "requester", "assignee", "originDepartment", "targetDepartment"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    /** Exportação em CSV (administrador): todos os chamados, numa consulta só, em ordem de número. */
    @Query("""
            select new br.com.contabilizape.centraldesk.dto.TicketExportRow(
                t.number, t.title, t.description, r.fullName, o.name, d.name, a.fullName, t.status,
                t.createdAt, t.firstAssignedAt, t.closedAt, t.updatedAt)
            from Ticket t join t.requester r join t.originDepartment o join t.targetDepartment d
            left join t.assignee a
            order by t.number
            """)
    List<TicketExportRow> findAllForExport();

    /** Exportação em CSV (coordenador): só os chamados que o setor abriu ou recebeu. */
    @Query("""
            select new br.com.contabilizape.centraldesk.dto.TicketExportRow(
                t.number, t.title, t.description, r.fullName, o.name, d.name, a.fullName, t.status,
                t.createdAt, t.firstAssignedAt, t.closedAt, t.updatedAt)
            from Ticket t join t.requester r join t.originDepartment o join t.targetDepartment d
            left join t.assignee a
            where o.id = :sector or d.id = :sector
            order by t.number
            """)
    List<TicketExportRow> findSectorForExport(@Param("sector") UUID sector);
}

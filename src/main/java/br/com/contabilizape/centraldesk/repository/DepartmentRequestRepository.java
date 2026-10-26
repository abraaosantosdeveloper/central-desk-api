package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DepartmentRequestRepository extends JpaRepository<DepartmentRequest, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, DepartmentRequestStatus status);

    long countByStatus(DepartmentRequestStatus status);

    /** Fila de aprovação dos admins, do mais antigo para o mais novo (índice da V9). */
    @EntityGraph(attributePaths = {"user", "department"})
    List<DepartmentRequest> findByStatusOrderByCreatedAtAsc(DepartmentRequestStatus status);

    /** Trava o pedido: dois admins não decidem o mesmo pedido ao mesmo tempo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DepartmentRequest r where r.id = :id")
    Optional<DepartmentRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r.user.id from DepartmentRequest r where r.status = :status")
    Set<UUID> findUserIdsByStatus(@Param("status") DepartmentRequestStatus status);
}

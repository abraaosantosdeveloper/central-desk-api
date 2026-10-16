package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.DepartmentRequest;
import br.com.contabilizape.centraldesk.model.DepartmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRequestRepository extends JpaRepository<DepartmentRequest, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, DepartmentRequestStatus status);

}

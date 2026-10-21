package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    boolean existsByCpf(String cpf);

    @Query("select a.user from Admin a where a.user.status = :status")
    List<User> findAdminsByStatus(@Param("status") UserStatus status);

    /** Listagem do painel do admin: o departamento vem junto, sem uma consulta por conta. */
    @Override
    @EntityGraph(attributePaths = "department")
    Page<User> findAll(Specification<User> spec, Pageable pageable);
}

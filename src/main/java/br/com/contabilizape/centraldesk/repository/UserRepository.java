package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);

    @Query("select a.user from Admin a where a.user.status = :status")
    List<User> findAdminsByStatus(@Param("status") UserStatus status);

} 

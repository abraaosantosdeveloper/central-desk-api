package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    /** Ids de todos os administradores (são poucos): marca quem é admin na listagem de contas. */
    @Query("select a.userId from Admin a")
    Set<UUID> findAllUserIds();
}

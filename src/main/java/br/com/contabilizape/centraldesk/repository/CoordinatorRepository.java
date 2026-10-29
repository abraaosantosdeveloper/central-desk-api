package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Coordinator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface CoordinatorRepository extends JpaRepository<Coordinator, UUID> {

    /** Ids de todos os coordenadores (são poucos): marca quem coordena na listagem de contas. */
    @Query("select c.userId from Coordinator c")
    Set<UUID> findAllUserIds();
}

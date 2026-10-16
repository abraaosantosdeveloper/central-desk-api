package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriorityRepository extends JpaRepository<Priority, Short> {
}

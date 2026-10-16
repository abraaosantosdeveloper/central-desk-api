package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminRepository extends JpaRepository<Admin, UUID> {
}

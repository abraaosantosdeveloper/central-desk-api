package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

}

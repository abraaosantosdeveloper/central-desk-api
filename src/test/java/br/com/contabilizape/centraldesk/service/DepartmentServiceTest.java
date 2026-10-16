package br.com.contabilizape.centraldesk.service;

import br.com.contabilizape.centraldesk.dto.DepartmentResponse;
import br.com.contabilizape.centraldesk.model.Department;
import br.com.contabilizape.centraldesk.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void listsDepartmentsOrderedByNameAsIdAndName() {
        UUID financeId = UUID.randomUUID();
        UUID generalId = UUID.randomUUID();
        // Os mocks são criados antes: configurá-los dentro do thenReturn gera UnfinishedStubbingException
        Department finance = department(financeId, "Financeiro");
        Department general = department(generalId, "Geral");
        when(departmentRepository.findAll(Sort.by("name"))).thenReturn(List.of(finance, general));

        List<DepartmentResponse> departments = departmentService.listAll();

        assertThat(departments).containsExactly(
                new DepartmentResponse(financeId, "Financeiro"),
                new DepartmentResponse(generalId, "Geral"));
    }

    @Test
    void returnsEmptyListWhenThereAreNoDepartments() {
        when(departmentRepository.findAll(Sort.by("name"))).thenReturn(List.of());

        assertThat(departmentService.listAll()).isEmpty();
    }

    private static Department department(UUID id, String name) {
        Department department = mock(Department.class);
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        return department;
    }
}

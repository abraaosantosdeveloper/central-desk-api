package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.DepartmentApi;
import br.com.contabilizape.centraldesk.dto.DepartmentResponse;
import br.com.contabilizape.centraldesk.service.DepartmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lista pública de departamentos, usada pelo select do formulário de cadastro
 * (quem ainda não tem conta precisa enxergá-la).
 */
@RestController
@RequestMapping("/departments")
public class DepartmentController implements DepartmentApi {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @Override
    @GetMapping
    public List<DepartmentResponse> list() {
        return departmentService.listAll();
    }
}

package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.DepartmentRequestApi;
import br.com.contabilizape.centraldesk.dto.DepartmentRequestResponse;
import br.com.contabilizape.centraldesk.service.DepartmentRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Só ROLE_ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/department-requests")
public class DepartmentRequestController implements DepartmentRequestApi {

    private final DepartmentRequestService departmentRequestService;

    public DepartmentRequestController(DepartmentRequestService departmentRequestService) {
        this.departmentRequestService = departmentRequestService;
    }

    @Override
    @GetMapping
    public List<DepartmentRequestResponse> listPending() {
        return departmentRequestService.listPending();
    }

    @Override
    @PatchMapping("/{id}/approve")
    public DepartmentRequestResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return departmentRequestService.approve(id, UUID.fromString(jwt.getSubject()));
    }

    @Override
    @PatchMapping("/{id}/reject")
    public DepartmentRequestResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return departmentRequestService.reject(id, UUID.fromString(jwt.getSubject()));
    }
}

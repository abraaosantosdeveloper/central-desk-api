package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AdminUserApi;
import br.com.contabilizape.centraldesk.dto.AdminUserDetailResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserUpdateRequest;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import br.com.contabilizape.centraldesk.service.UserAdministrationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import br.com.contabilizape.centraldesk.dto.DepartmentChangeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Contas vistas pelo administrador. Só ROLE_ADMIN (regra no SecurityConfig); as rotas /users/me
 * ficam no UserController e valem para qualquer usuário logado.
 */
@RestController
@RequestMapping("/users")
public class AdminUserController implements AdminUserApi {

    private final UserAdministrationService userAdministrationService;

    public AdminUserController(UserAdministrationService userAdministrationService) {
        this.userAdministrationService = userAdministrationService;
    }

    @Override
    @GetMapping
    public PageResponse<AdminUserResponse> list(UserFilter filter, @PageableDefault(size = 20) Pageable pageable) {
        return userAdministrationService.list(filter, pageable);
    }

    @Override
    @GetMapping("/{id}")
    public AdminUserDetailResponse get(@PathVariable UUID id) {
        return userAdministrationService.get(id);
    }

    @Override
    @PutMapping("/{id}")
    public AdminUserDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody AdminUserUpdateRequest request) {
        return userAdministrationService.update(id, request, UUID.fromString(jwt.getSubject()));
    }

    @Override
    @PatchMapping("/{id}/deactivate")
    public AdminUserResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userAdministrationService.deactivate(id, UUID.fromString(jwt.getSubject()));
    }

    @Override
    @PatchMapping("/{id}/reactivate")
    public AdminUserResponse reactivate(@PathVariable UUID id) {
        return userAdministrationService.reactivate(id);
    }

    @Override
    @PatchMapping("/{id}/department")
    public AdminUserResponse changeDepartment(@PathVariable UUID id, @Valid @RequestBody DepartmentChangeRequest request) {
        return userAdministrationService.changeDepartment(id, request.departmentId());
    }

    @Override
    @PutMapping("/{id}/admin")
    public AdminUserResponse grantAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userAdministrationService.grantAdmin(id, UUID.fromString(jwt.getSubject()));
    }

    @Override
    @DeleteMapping("/{id}/admin")
    public AdminUserResponse revokeAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userAdministrationService.revokeAdmin(id, UUID.fromString(jwt.getSubject()));
    }
}

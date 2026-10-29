package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AdminUserApi;
import br.com.contabilizape.centraldesk.dto.AdminUserDetailResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserResponse;
import br.com.contabilizape.centraldesk.dto.AdminUserUpdateRequest;
import br.com.contabilizape.centraldesk.dto.PageResponse;
import br.com.contabilizape.centraldesk.dto.PrivilegeChangeRequest;
import br.com.contabilizape.centraldesk.dto.UserFilter;
import br.com.contabilizape.centraldesk.service.UserAdministrationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import br.com.contabilizape.centraldesk.dto.DepartmentChangeRequest;
import jakarta.validation.Valid;
import br.com.contabilizape.centraldesk.dto.TemporaryPasswordResponse;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.service.AdminPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
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

    private final AdminPasswordResetService adminPasswordResetService;

    public AdminUserController(UserAdministrationService userAdministrationService,
                               AdminPasswordResetService adminPasswordResetService) {
        this.adminPasswordResetService = adminPasswordResetService;
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

    /** Senha temporária mostrada uma vez ao admin, para entregar pessoalmente (SCRUM-203). */
    @Override
    @PutMapping("/{id}/password-reset")
    public TemporaryPasswordResponse resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                   HttpServletRequest request) {
        return adminPasswordResetService.reset(id, UUID.fromString(jwt.getSubject()), ClientInfo.from(request));
    }

    @Override
    @PutMapping("/{id}/admin")
    public AdminUserResponse grantAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userAdministrationService.grantAdmin(id, UUID.fromString(jwt.getSubject()));
    }

    /** ADMIN, COORDINATOR ou COLLABORATOR, exclusivos entre si (SCRUM-219). */
    @Override
    @PutMapping("/{id}/privilege")
    public AdminUserResponse changePrivilege(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @Valid @RequestBody PrivilegeChangeRequest request,
                                             HttpServletRequest httpRequest) {
        return userAdministrationService.changePrivilege(id, request.level(), UUID.fromString(jwt.getSubject()),
                ClientInfo.from(httpRequest));
    }

    @Override
    @DeleteMapping("/{id}/admin")
    public AdminUserResponse revokeAdmin(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return userAdministrationService.revokeAdmin(id, UUID.fromString(jwt.getSubject()));
    }
}

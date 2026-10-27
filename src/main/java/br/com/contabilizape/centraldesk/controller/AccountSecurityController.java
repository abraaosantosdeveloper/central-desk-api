package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AccountSecurityApi;
import br.com.contabilizape.centraldesk.dto.MessageResponse;
import br.com.contabilizape.centraldesk.dto.TrustedDeviceResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorStatusResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorUpdateRequest;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import br.com.contabilizape.centraldesk.service.AuthService;
import br.com.contabilizape.centraldesk.service.TrustedDeviceService;
import br.com.contabilizape.centraldesk.service.TwoFactorSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Seção "Segurança" de Meu perfil (SCRUM-206). Qualquer usuário logado, sempre sobre a própria conta. */
@RestController
@RequestMapping("/users/me")
public class AccountSecurityController implements AccountSecurityApi {

    private static final MessageResponse CODE_SENT = new MessageResponse("Enviamos um código para o seu e-mail.");

    private final TwoFactorSettingsService twoFactorSettings;
    private final TrustedDeviceService trustedDevices;
    private final AuthService authService;

    public AccountSecurityController(TwoFactorSettingsService twoFactorSettings, TrustedDeviceService trustedDevices,
                                     AuthService authService) {
        this.twoFactorSettings = twoFactorSettings;
        this.trustedDevices = trustedDevices;
        this.authService = authService;
    }

    @Override
    @GetMapping("/two-factor")
    public TwoFactorStatusResponse twoFactor(@AuthenticationPrincipal Jwt jwt) {
        return twoFactorSettings.status(userId(jwt));
    }

    @Override
    @PostMapping("/two-factor/code")
    public ResponseEntity<MessageResponse> sendTwoFactorDisableCode(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        twoFactorSettings.sendDisableCode(userId(jwt), ClientInfo.from(request));
        return ResponseEntity.accepted().body(CODE_SENT);
    }

    @Override
    @PutMapping("/two-factor")
    public TwoFactorStatusResponse updateTwoFactor(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody @Valid TwoFactorUpdateRequest body,
                                                   HttpServletRequest request) {
        return twoFactorSettings.update(userId(jwt), body, ClientInfo.from(request));
    }

    @Override
    @PostMapping("/password/code")
    public ResponseEntity<MessageResponse> sendPasswordChangeCode(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        authService.sendPasswordChangeCode(userId(jwt), ClientInfo.from(request));
        return ResponseEntity.accepted().body(CODE_SENT);
    }

    @Override
    @GetMapping("/trusted-devices")
    public List<TrustedDeviceResponse> trustedDevices(@AuthenticationPrincipal Jwt jwt) {
        return trustedDevices.list(userId(jwt));
    }

    @Override
    @DeleteMapping("/trusted-devices/{id}")
    public ResponseEntity<Void> removeTrustedDevice(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        trustedDevices.revoke(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/trusted-devices")
    public ResponseEntity<Void> removeAllTrustedDevices(@AuthenticationPrincipal Jwt jwt) {
        trustedDevices.revokeAll(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

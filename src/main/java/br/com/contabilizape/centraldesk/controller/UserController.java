package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.UserApi;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.PasswordChangeRequest;
import br.com.contabilizape.centraldesk.dto.UserProfileUpdateRequest;
import br.com.contabilizape.centraldesk.security.AccessTokenCookie;
import br.com.contabilizape.centraldesk.security.RefreshTokenCookie;
import br.com.contabilizape.centraldesk.security.TokenService;
import br.com.contabilizape.centraldesk.service.AuthService;
import br.com.contabilizape.centraldesk.service.AuthenticatedSession;
import br.com.contabilizape.centraldesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController implements UserApi {

    private final UserService userService;
    private final AuthService authService;
    private final AccessTokenCookie accessTokenCookie;
    private final RefreshTokenCookie refreshTokenCookie;

    public UserController(UserService userService, AuthService authService,
                          AccessTokenCookie accessTokenCookie, RefreshTokenCookie refreshTokenCookie) {
        this.userService = userService;
        this.authService = authService;
        this.accessTokenCookie = accessTokenCookie;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @Override
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(userId(jwt), roles(jwt));
    }

    @Override
    @PutMapping("/me")
    public CurrentUserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody @Valid UserProfileUpdateRequest request) {
        return userService.updateProfile(userId(jwt), request, roles(jwt));
    }

    /** As outras sessões caem; esta recebe cookies novos (o refresh_token antigo não chega aqui, por ter Path=/auth). */
    @Override
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody @Valid PasswordChangeRequest request) {
        AuthenticatedSession session = authService.changePassword(userId(jwt), request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        accessTokenCookie.create(session.accessToken()).toString(),
                        refreshTokenCookie.create(session.refreshToken()).toString())
                .build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static List<String> roles(Jwt jwt) {
        return Objects.requireNonNullElse(jwt.getClaimAsStringList(TokenService.ROLES_CLAIM), List.of());
    }
}

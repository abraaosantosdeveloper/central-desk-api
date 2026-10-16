package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AuthApi;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import br.com.contabilizape.centraldesk.security.AccessTokenCookie;
import br.com.contabilizape.centraldesk.security.RefreshTokenCookie;
import br.com.contabilizape.centraldesk.service.AuthService;
import br.com.contabilizape.centraldesk.service.AuthenticatedSession;
import br.com.contabilizape.centraldesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController implements AuthApi {

    private final UserService userService;
    private final AuthService authService;
    private final AccessTokenCookie accessTokenCookie;
    private final RefreshTokenCookie refreshTokenCookie;

    public AuthController(UserService userService, AuthService authService,
                          AccessTokenCookie accessTokenCookie, RefreshTokenCookie refreshTokenCookie) {
        this.userService = userService;
        this.authService = authService;
        this.accessTokenCookie = accessTokenCookie;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid UserCreateRequest request) {
        UserResponse response = userService.create(request);
        // O recurso criado é um usuário, por isso o Location aponta para /users
        URI location = URI.create("/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<CurrentUserResponse> login(@RequestBody @Valid LoginRequest request) {
        return withSessionCookies(authService.login(request));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<CurrentUserResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        return withSessionCookies(authService.refresh(refreshToken));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.clear().toString(), refreshTokenCookie.clear().toString())
                .build();
    }

    // O access token vai primeiro: quem lê só o primeiro Set-Cookie recebe o token da API
    private ResponseEntity<CurrentUserResponse> withSessionCookies(AuthenticatedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        accessTokenCookie.create(session.accessToken()).toString(),
                        refreshTokenCookie.create(session.refreshToken()).toString())
                .body(session.user());
    }
}

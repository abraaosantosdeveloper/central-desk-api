package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AuthApi;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import br.com.contabilizape.centraldesk.security.AccessTokenCookie;
import br.com.contabilizape.centraldesk.service.AuthService;
import br.com.contabilizape.centraldesk.service.AuthenticatedSession;
import br.com.contabilizape.centraldesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

    public AuthController(UserService userService, AuthService authService, AccessTokenCookie accessTokenCookie) {
        this.userService = userService;
        this.authService = authService;
        this.accessTokenCookie = accessTokenCookie;
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
        AuthenticatedSession session = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.create(session.accessToken()).toString())
                .body(session.user());
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessTokenCookie.clear().toString())
                .build();
    }
}

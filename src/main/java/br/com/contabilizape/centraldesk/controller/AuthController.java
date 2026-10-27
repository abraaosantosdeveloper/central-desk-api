package br.com.contabilizape.centraldesk.controller;

import br.com.contabilizape.centraldesk.controller.doc.AuthApi;
import br.com.contabilizape.centraldesk.dto.CurrentUserResponse;
import br.com.contabilizape.centraldesk.dto.EmailVerificationRequest;
import br.com.contabilizape.centraldesk.dto.ForgotPasswordRequest;
import br.com.contabilizape.centraldesk.dto.ResendCodeRequest;
import br.com.contabilizape.centraldesk.service.EmailVerificationService;
import br.com.contabilizape.centraldesk.dto.LoginRequest;
import br.com.contabilizape.centraldesk.dto.MessageResponse;
import br.com.contabilizape.centraldesk.dto.PasswordResetRequest;
import br.com.contabilizape.centraldesk.service.PasswordRecoveryService;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.dto.UserResponse;
import br.com.contabilizape.centraldesk.security.AccessTokenCookie;
import br.com.contabilizape.centraldesk.security.RefreshTokenCookie;
import br.com.contabilizape.centraldesk.service.AuthService;
import br.com.contabilizape.centraldesk.service.AuthenticatedSession;
import br.com.contabilizape.centraldesk.service.UserService;
import br.com.contabilizape.centraldesk.security.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PasswordRecoveryService passwordRecoveryService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(UserService userService, AuthService authService,
                          AccessTokenCookie accessTokenCookie, RefreshTokenCookie refreshTokenCookie,
                          PasswordRecoveryService passwordRecoveryService,
                          EmailVerificationService emailVerificationService) {
        this.passwordRecoveryService = passwordRecoveryService;
        this.emailVerificationService = emailVerificationService;
        this.userService = userService;
        this.authService = authService;
        this.accessTokenCookie = accessTokenCookie;
        this.refreshTokenCookie = refreshTokenCookie;
    }

    @Override
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid UserCreateRequest request,
                                                 HttpServletRequest httpRequest) {
        UserResponse response = userService.create(request, ClientInfo.from(httpRequest));
        // O recurso criado é um usuário, por isso o Location aponta para /users
        URI location = URI.create("/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<CurrentUserResponse> login(@RequestBody @Valid LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        return withSessionCookies(authService.login(request, ClientInfo.from(httpRequest)));
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

    /** Código certo: a conta é ativada e a sessão já é aberta (SCRUM-205). */
    @Override
    @PostMapping("/email/verify")
    public ResponseEntity<CurrentUserResponse> verifyEmail(@RequestBody @Valid EmailVerificationRequest request,
                                                           HttpServletRequest httpRequest) {
        return withSessionCookies(emailVerificationService.verify(request, ClientInfo.from(httpRequest)).session());
    }

    @Override
    @PostMapping("/email/resend")
    public ResponseEntity<MessageResponse> resendVerificationCode(@RequestBody @Valid ResendCodeRequest request,
                                                                  HttpServletRequest httpRequest) {
        emailVerificationService.resend(request.email(), ClientInfo.from(httpRequest));
        return ResponseEntity.accepted().body(new MessageResponse(
                "Se houver um cadastro aguardando confirmação para este e-mail, enviamos um novo código."));
    }

    /** Sempre 202 com a mesma mensagem, exista a conta ou não (SCRUM-204). */
    @Override
    @PostMapping("/password/forgot")
    public ResponseEntity<MessageResponse> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request,
                                                          HttpServletRequest httpRequest) {
        passwordRecoveryService.requestReset(request.email(), ClientInfo.from(httpRequest));
        return ResponseEntity.accepted().body(new MessageResponse(
                "Se o e-mail estiver cadastrado, você vai receber um código para redefinir a senha."));
    }

    @Override
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid PasswordResetRequest request,
                                              HttpServletRequest httpRequest) {
        passwordRecoveryService.reset(request, ClientInfo.from(httpRequest));
        return ResponseEntity.noContent().build();
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

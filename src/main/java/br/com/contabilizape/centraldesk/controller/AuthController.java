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
import br.com.contabilizape.centraldesk.dto.TwoFactorChallengeResponse;
import br.com.contabilizape.centraldesk.dto.TwoFactorCodeRequest;
import br.com.contabilizape.centraldesk.dto.UserCreateRequest;
import br.com.contabilizape.centraldesk.security.AuthFlowCookies;
import br.com.contabilizape.centraldesk.service.LoginChallengeService;
import br.com.contabilizape.centraldesk.service.LoginOutcome;
import br.com.contabilizape.centraldesk.service.TwoFactorLoginService;
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
    private final TwoFactorLoginService twoFactorLoginService;
    private final LoginChallengeService loginChallengeService;
    private final AuthFlowCookies authFlowCookies;

    public AuthController(UserService userService, AuthService authService,
                          AccessTokenCookie accessTokenCookie, RefreshTokenCookie refreshTokenCookie,
                          PasswordRecoveryService passwordRecoveryService,
                          EmailVerificationService emailVerificationService,
                          TwoFactorLoginService twoFactorLoginService, LoginChallengeService loginChallengeService,
                          AuthFlowCookies authFlowCookies) {
        this.twoFactorLoginService = twoFactorLoginService;
        this.loginChallengeService = loginChallengeService;
        this.authFlowCookies = authFlowCookies;
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
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest,
                                   @CookieValue(name = AuthFlowCookies.TRUSTED_DEVICE, required = false) String trustedDevice) {
        LoginOutcome outcome = authService.login(request, ClientInfo.from(httpRequest), trustedDevice);
        if (outcome.isTwoFactorRequired()) {
            // Nenhum token de sessão: só o desafio, até o código do e-mail ser confirmado
            return ResponseEntity.accepted()
                    .header(HttpHeaders.SET_COOKIE, authFlowCookies.challenge(outcome.challengeToken()).toString())
                    .body(new TwoFactorChallengeResponse(true));
        }
        return withSessionCookies(outcome.session());
    }

    /** Segunda etapa do login (SCRUM-206): o código do e-mail, com o cookie do desafio. */
    @Override
    @PostMapping("/login/tfa")
    public ResponseEntity<CurrentUserResponse> completeLogin(
            @RequestBody @Valid TwoFactorCodeRequest request, HttpServletRequest httpRequest,
            @CookieValue(name = AuthFlowCookies.CHALLENGE, required = false) String challenge) {
        TwoFactorLoginService.Completed completed = twoFactorLoginService.complete(
                challenge, request.code(), request.trustThisBrowser(), ClientInfo.from(httpRequest));
        var response = ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        accessTokenCookie.create(completed.session().accessToken()).toString(),
                        refreshTokenCookie.create(completed.session().refreshToken()).toString(),
                        authFlowCookies.clearChallenge().toString());
        if (completed.trustedDeviceToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, authFlowCookies.trustedDevice(completed.trustedDeviceToken()).toString());
        }
        return response.body(completed.session().user());
    }

    @Override
    @PostMapping("/login/tfa/resend")
    public ResponseEntity<MessageResponse> resendLoginCode(
            HttpServletRequest httpRequest,
            @CookieValue(name = AuthFlowCookies.CHALLENGE, required = false) String challenge) {
        loginChallengeService.resend(challenge, ClientInfo.from(httpRequest));
        return ResponseEntity.accepted().body(new MessageResponse("Enviamos um novo código para o seu e-mail."));
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
        EmailVerificationService.ConfirmedAccount confirmed = emailVerificationService.verify(request, ClientInfo.from(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        accessTokenCookie.create(confirmed.session().accessToken()).toString(),
                        refreshTokenCookie.create(confirmed.session().refreshToken()).toString(),
                        authFlowCookies.trustedDevice(confirmed.trustedDeviceToken()).toString())
                .body(confirmed.session().user());
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

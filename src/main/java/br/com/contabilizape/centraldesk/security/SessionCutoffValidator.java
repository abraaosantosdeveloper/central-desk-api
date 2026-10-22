package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Recusa access tokens emitidos antes do corte de sessões do usuário (users.sessions_valid_from).
 * Sem isso, encerrar as sessões só derrubava os refresh tokens e o access token em uso valia até
 * vencer (15 min). O `iat` do JWT tem precisão de segundos e o corte é gravado truncado no segundo:
 * o token emitido logo depois do corte (ex.: a sessão atual na troca de senha) continua valendo.
 */
@Component
public class SessionCutoffValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSION_ENDED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Sessão encerrada. Entre novamente.", null);

    private final UserRepository userRepository;

    public SessionCutoffValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(SESSION_ENDED);
        }
        Optional<OffsetDateTime> cutoff = userRepository.findSessionsValidFrom(userId);
        Instant issuedAt = jwt.getIssuedAt();
        if (cutoff.isPresent() && (issuedAt == null || issuedAt.isBefore(cutoff.get().toInstant()))) {
            return OAuth2TokenValidatorResult.failure(SESSION_ENDED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}

-- Trilha de auditoria da segurança das contas (SCRUM-202, PLANO-SEGURANCA §3 e §5):
-- tentativas de login, bloqueios, códigos errados, redefinições de senha, navegadores novos.
CREATE TABLE security_events (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Nulo quando a tentativa foi com um e-mail que não existe
    user_id    UUID         REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL
               CHECK (type IN ('LOGIN_FAILED', 'LOGIN_UNKNOWN_EMAIL', 'LOGIN_BLOCKED_ACCOUNT', 'ACCOUNT_BLOCKED',
                               'TFA_FAILED', 'NEW_TRUSTED_BROWSER', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED',
                               'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_BLOCKED_ACCOUNT',
                               'PASSWORD_RESET_COMPLETED', 'PASSWORD_RESET_BY_ADMIN', 'ADMIN_RECOVERY',
                               'EMAIL_VERIFIED')),
    ip         VARCHAR(45),
    user_agent VARCHAR(255),
    details    VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_events_user ON security_events (user_id, type, created_at);
CREATE INDEX idx_security_events_created_at ON security_events (created_at);

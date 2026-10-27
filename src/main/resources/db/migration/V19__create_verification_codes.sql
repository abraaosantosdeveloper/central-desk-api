-- Códigos de 6 dígitos enviados por e-mail (SCRUM-201, PLANO-SEGURANCA §1 e §4).
-- Só o HMAC-SHA256 (chave OTP_HMAC_KEY, fora do banco) é guardado: o vazamento da tabela não revela código.
CREATE TABLE verification_codes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     VARCHAR(30) NOT NULL
                CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'LOGIN_TFA', 'TFA_DISABLE', 'PASSWORD_CHANGE')),
    code_hmac   CHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    SMALLINT    NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_ip  VARCHAR(45)
);

-- Código vigente, intervalo entre pedidos e limite por hora (por conta e finalidade)
CREATE INDEX idx_verification_codes_user_purpose ON verification_codes (user_id, purpose, created_at);
-- Limite por IP
CREATE INDEX idx_verification_codes_ip ON verification_codes (request_ip, created_at);

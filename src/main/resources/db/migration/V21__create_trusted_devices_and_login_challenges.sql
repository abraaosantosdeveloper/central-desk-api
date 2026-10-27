-- Verificação em duas etapas (SCRUM-206, PLANO-SEGURANCA §9).

-- Navegador confiável: o "id do navegador" é um token aleatório num cookie HttpOnly (Path=/auth, 30 dias);
-- aqui fica só o hash SHA-256. Cada navegador tem o seu token e o seu prazo.
CREATE TABLE trusted_devices (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   CHAR(64)     NOT NULL UNIQUE,
    label        VARCHAR(120) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_trusted_devices_user_active ON trusted_devices (user_id, created_at) WHERE revoked_at IS NULL;

-- Desafio do login: depois da senha certa, o cookie tfa_challenge (5 min) amarra o código à conta e a esta
-- tentativa. Nenhum token de sessão existe até o código ser confirmado.
CREATE TABLE login_challenges (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash        CHAR(64)    NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ NOT NULL,
    failed_attempts   SMALLINT    NOT NULL DEFAULT 0,
    -- O dono é avisado uma vez por desafio quando alguém erra o código depois de acertar a senha
    failure_notified  BOOLEAN     NOT NULL DEFAULT FALSE,
    consumed_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip                VARCHAR(45)
);

CREATE INDEX idx_login_challenges_expires_at ON login_challenges (expires_at);

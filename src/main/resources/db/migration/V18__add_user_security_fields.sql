-- Segurança das contas (SCRUM-200, PLANO-SEGURANCA-CONTAS-E-EMAILS.md §3).
-- O bloqueio é um estado da conta (status = BLOCKED), não uma tabela à parte: login, renovação de sessão
-- e todas as rotas já conferem o status.

-- O CHECK da V2 só aceita ACTIVE, DEACTIVATED e ANONYMIZED. O nome foi gerado pelo Postgres:
-- procura pela definição em vez de supor o nome.
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'users'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%status%';
    IF constraint_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(constraint_name);
    END IF;
END $$;

ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'ANONYMIZED', 'PENDING_VERIFICATION', 'BLOCKED'));

ALTER TABLE users
    -- Falhas seguidas desde o último acesso completo (senha + código); na 5ª, BLOCKED
    ADD COLUMN failed_login_attempts         SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN blocked_at                    TIMESTAMPTZ,
    ADD COLUMN email_verified_at             TIMESTAMPTZ,
    -- Verificação em duas etapas: ligada por padrão (administradores não podem desligar)
    ADD COLUMN two_factor_enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Senha temporária dada por um administrador: vale 24 h e obriga a troca no primeiro acesso
    ADD COLUMN password_change_required      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN temporary_password_expires_at TIMESTAMPTZ,
    -- Departamento pedido no cadastro, guardado até o e-mail ser confirmado (o pedido da Inovação só nasce depois)
    ADD COLUMN requested_department_id       UUID        REFERENCES departments (id);

-- Contas que já existem: o e-mail delas é considerado confirmado
UPDATE users SET email_verified_at = created_at WHERE status <> 'ANONYMIZED';

-- Rotina diária apaga cadastros não confirmados em 24 h
CREATE INDEX idx_users_pending_created_at ON users (created_at) WHERE status = 'PENDING_VERIFICATION';

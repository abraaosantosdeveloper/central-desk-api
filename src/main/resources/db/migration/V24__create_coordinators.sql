-- SCRUM-219 (DECISOES-PAINEIS-METRICAS-BI §4 e PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS §3): papel de coordenador.
-- O coordenador coordena o próprio departamento (users.department_id); o setor não é copiado para cá, então
-- não há como ficar coordenando um setor em que a pessoa não está. Mais de um coordenador por setor é permitido.
-- Os privilégios são exclusivos (PV1): a mesma conta não fica em admins e em coordinators (regra na aplicação).
CREATE TABLE coordinators (
    user_id        UUID        PRIMARY KEY REFERENCES users (id),
    granted_by_id  UUID        REFERENCES users (id),
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Mudança de privilégio entra na trilha de auditoria (PV7). O CHECK da V20 não tem nome explícito.
ALTER TABLE security_events DROP CONSTRAINT security_events_type_check;
ALTER TABLE security_events ADD CONSTRAINT chk_security_events_type
    CHECK (type IN ('LOGIN_FAILED', 'LOGIN_UNKNOWN_EMAIL', 'LOGIN_BLOCKED_ACCOUNT', 'ACCOUNT_BLOCKED',
                    'TFA_FAILED', 'NEW_TRUSTED_BROWSER', 'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED',
                    'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED', 'PASSWORD_RESET_BLOCKED_ACCOUNT',
                    'PASSWORD_RESET_COMPLETED', 'PASSWORD_RESET_BY_ADMIN', 'ADMIN_RECOVERY',
                    'EMAIL_VERIFIED', 'PRIVILEGE_CHANGED'));

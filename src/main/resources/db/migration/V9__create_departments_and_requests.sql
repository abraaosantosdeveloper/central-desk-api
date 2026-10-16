CREATE TABLE department_requests (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id),
    department_id  UUID         NOT NULL REFERENCES departments (id),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by_id UUID         REFERENCES users (id),
    reviewed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_department_requests_review CHECK (
        (status = 'PENDING' AND reviewed_by_id IS NULL AND reviewed_at IS NULL)
        OR
        (status <> 'PENDING' AND reviewed_by_id IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

-- No máximo um pedido pendente por usuário
CREATE UNIQUE INDEX uq_department_requests_pending_user
    ON department_requests (user_id)
    WHERE status = 'PENDING';

-- Histórico de pedidos de um usuário
CREATE INDEX idx_department_requests_user_id
    ON department_requests (user_id);

-- Fila de aprovação dos admins (pendentes, do mais antigo ao mais novo)
CREATE INDEX idx_department_requests_status_created_at
    ON department_requests (status, created_at);
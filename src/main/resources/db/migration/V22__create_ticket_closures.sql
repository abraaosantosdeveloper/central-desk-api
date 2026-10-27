-- Reabertura de chamados (SCRUM-208, PLANO-SEGURANCA §13.2). Ao reabrir, o encerramento atual vem para cá,
-- com quem reabriu e o motivo, e os campos closed_* do chamado são limpos. Limite de 3 reaberturas por chamado.
CREATE TABLE ticket_closures (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id      UUID          NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    closed_by_id   UUID          REFERENCES users (id),
    closed_at      TIMESTAMPTZ   NOT NULL,
    resolution     VARCHAR(2000),
    reopened_by_id UUID          NOT NULL REFERENCES users (id),
    reopened_at    TIMESTAMPTZ   NOT NULL,
    reopen_reason  VARCHAR(1000) NOT NULL
);

CREATE INDEX idx_ticket_closures_ticket ON ticket_closures (ticket_id, reopened_at);

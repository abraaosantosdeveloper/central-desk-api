-- Avaliação do atendimento: no máximo uma por chamado, feita pelo solicitante.
-- rated_id guarda o responsável no momento do encerramento, para que uma
-- reatribuição posterior não mude quem foi avaliado.
-- Sem updated_at: a avaliação não é editada depois de enviada.
CREATE TABLE ticket_ratings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id  UUID        NOT NULL REFERENCES tickets (id),
    rater_id   UUID        NOT NULL REFERENCES users (id),
    rated_id   UUID        NOT NULL REFERENCES users (id),
    score      SMALLINT    NOT NULL,
    comment    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ticket_ratings_ticket UNIQUE (ticket_id),
    CONSTRAINT chk_ticket_ratings_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT chk_ticket_ratings_not_self CHECK (rater_id <> rated_id)
);

-- Consultas de desempenho por profissional (médias, histórico)
CREATE INDEX idx_ticket_ratings_rated ON ticket_ratings (rated_id, created_at);

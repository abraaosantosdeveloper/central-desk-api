-- SCRUM-216 (DECISOES-PAINEIS-METRICAS-BI §6): primeira vez que o chamado foi assumido. O assigned_at é zerado
-- ao reabrir sem manter o responsável e ao devolver à fila; sem esta coluna o tempo de resposta do painel de
-- métricas perderia justamente os chamados que voltaram. Preenchida uma única vez, na primeira atribuição.
ALTER TABLE tickets ADD COLUMN first_assigned_at TIMESTAMPTZ;

-- Povoamento: o melhor dado que existe hoje é o assigned_at de quem ainda está atribuído. Chamados que já
-- voltaram à fila antes desta migration ficam sem valor (e fora das médias de resposta).
UPDATE tickets SET first_assigned_at = assigned_at WHERE assigned_at IS NOT NULL;

-- Filtro do painel de métricas por encerramento (o índice de created_at já existe, desde a V23)
CREATE INDEX idx_tickets_closed_at ON tickets (closed_at) WHERE closed_at IS NOT NULL;

-- SCRUM-167: categorias removidas pelo administrador são arquivadas, não apagadas,
-- para preservar o histórico dos chamados que já as usam.
ALTER TABLE categories ADD COLUMN archived_at TIMESTAMPTZ;

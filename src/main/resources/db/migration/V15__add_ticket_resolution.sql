-- SCRUM-174: ao encerrar, quem atende registra o que foi feito. Fica no próprio chamado
-- (um texto por chamado). Chamados encerrados antes desta versão ficam sem o registro.
ALTER TABLE tickets ADD COLUMN resolution TEXT;

ALTER TABLE tickets ADD CONSTRAINT chk_tickets_resolution_length
    CHECK (resolution IS NULL OR char_length(resolution) <= 2000);

-- Corte de sessões (SCRUM-176): access tokens emitidos antes deste instante deixam de valer na hora.
-- Preenchido quando o e-mail de login muda pelo admin ou a senha é trocada; vazio = nenhum corte.
ALTER TABLE users ADD COLUMN sessions_valid_from TIMESTAMPTZ;

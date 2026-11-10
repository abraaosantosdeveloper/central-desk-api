-- Edição community: nomes genéricos para os departamentos fixos e ajuste das categorias iniciais.
-- A V8 e a V10 já foram aplicadas em bancos existentes e não podem mudar (o Flyway valida o
-- checksum delas), então a troca de nomes entra aqui. Funciona tanto em banco novo quanto em
-- banco que já tinha a V8/V10 originais.

-- Departamentos fixos (os ids continuam os do application.properties)
UPDATE departments SET name = 'TI & Suporte' WHERE id = 'e9790f1f-41df-4369-bfe7-1be4be535561';
UPDATE departments SET name = 'Comunidade'   WHERE id = '3094ed3f-42ab-4bc0-9d8d-f6eb08891016';

-- Nova categoria
INSERT INTO categories (label, color) VALUES ('Serviços', '#10B981')
ON CONFLICT (label) DO NOTHING;

-- Categorias que saem: apagadas se nenhum chamado as usa; arquivadas (V13) se já houver
-- chamados com elas, para preservar o histórico.
DELETE FROM categories c
 WHERE c.label IN ('Hardware', 'Rede e internet', 'Sistemas internos')
   AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.category_id = c.id);

UPDATE categories
   SET archived_at = now()
 WHERE label IN ('Hardware', 'Rede e internet', 'Sistemas internos')
   AND archived_at IS NULL;

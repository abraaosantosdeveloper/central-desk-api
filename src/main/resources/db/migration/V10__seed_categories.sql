-- Categorias iniciais dos chamados. A tabela existe desde a V4, mas estava vazia.
-- Novas categorias entram por migration (ou, no futuro, por uma tela do administrador).
INSERT INTO categories (label, color) VALUES
    ('Software',          '#3B82F6'),
    ('Acesso e contas',   '#F59E0B'),
    ('Serviços',           '#10B981'),
    ('Outros',            '#6B7280');

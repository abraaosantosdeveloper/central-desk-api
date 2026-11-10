-- Categorias iniciais dos chamados. A tabela existe desde a V4, mas estava vazia.
-- Novas categorias entram por migration (ou, no futuro, por uma tela do administrador).
INSERT INTO categories (label, color) VALUES
    ('Hardware',          '#EF4444'),
    ('Software',          '#3B82F6'),
    ('Acesso e contas',   '#F59E0B'),
    ('Rede e internet',   '#10B981'),
    ('Sistemas internos', '#8B5CF6'),
    ('Outros',            '#6B7280');

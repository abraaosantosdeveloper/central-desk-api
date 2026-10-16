# Como contribuir com o CentralDesk

Fluxo de trabalho do repositório: branches, commits, pull requests e versões. Vale para todo o time e para o assistente.

## Branches

A `main` está **sempre estável**: compila, e `.\mvnw test` passa. Nada é commitado direto nela. Todo trabalho acontece numa branch curta, que volta para a `main` por pull request.

### Tipos

| Prefixo | Quando usar | Exemplo | Prefixo do commit |
|---|---|---|---|
| `feature/` | Funcionalidade nova (subtarefa, tarefa ou história) | `feature/SCRUM-122-swagger` | `feat` |
| `fix/` | Correção de bug encontrado em desenvolvimento (subtarefa `[BUG]` no Jira) | `fix/SCRUM-133-erros-por-campo` | `fix` |
| `hotfix/` | Correção **urgente** de algo que já está em produção: sai da `main` e volta para ela o quanto antes | `hotfix/SCRUM-140-login-bloqueado` | `fix` |
| `refactor/` | Muda a estrutura sem mudar o comportamento (os testes continuam iguais e verdes) | `refactor/SCRUM-141-excecoes-de-dominio` | `refactor` |
| `test/` | Só testes (novos ou corrigidos) | `test/SCRUM-129-testes-cadastro` | `test` |
| `docs/` | Só documentação (README, CONTEXT.md, ABNT, diagramas) | `docs/SCRUM-63-seed-v8` | `docs` |
| `chore/` | Dependências, build, configuração, CI | `chore/SCRUM-142-github-actions` | `chore` |
| `spike/` | Experimento descartável para tirar uma dúvida técnica; **não vira PR**, o aprendizado vai para o Jira ou o CONTEXT.md | `spike/sse-notificacoes` | — |

### Regras de nome

- Formato: `tipo/SCRUM-N-descricao-curta`. Com a chave do Jira no nome, o Jira liga branch, commits e PR ao item (com o app *GitHub for Jira* instalado).
- Minúsculas, palavras separadas por hífen, **sem acentos**, até umas cinco palavras.
- Só `spike/` e `chore/` sem item no Jira podem omitir a chave.

### Uma branch por quê?

- **Regra geral: uma branch por item entregue de uma vez**, normalmente a subtarefa (`feature/SCRUM-125-endpoint-departamentos`).
- **História pequena**, entregue num PR só: a branch é da própria história (`feature/SCRUM-118-cadastro`).
- **Vida curta**: poucos dias. Branch longa acumula conflito. Se crescer demais, divida o item no Jira.

## Fluxo do dia a dia

```powershell
# 1. Partir da main atualizada
git switch main
git pull

# 2. Criar a branch do item
git switch -c feature/SCRUM-122-swagger

# 3. Trabalhar em ciclos de TDD, com commits pequenos
.\mvnw test
git add <arquivos>
git commit -m "feat(api): ..." -m "Refs: SCRUM-122"

# 4. Trazer novidades da main, se ela andou
git pull --rebase origin main

# 5. Publicar e abrir o PR
git push -u origin feature/SCRUM-122-swagger
```

Depois do merge: `git switch main`, `git pull` e `git branch -d feature/SCRUM-122-swagger`.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/pt-br/), mensagem em português:

```
tipo(escopo): resumo no imperativo, em minúsculas

Corpo opcional: o que mudou e por quê.

Refs: SCRUM-N
```

- Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.
- Escopo: a área afetada (`auth`, `db`, `model`, `validation`, `departments`, `api`...).
- Um commit = uma mudança coerente, que compila sozinha.

## Pull requests

Título no mesmo formato do commit. Antes de pedir o merge:

- [ ] `.\mvnw test` passando (com o Docker aberto)
- [ ] Execução registrada em `docs/testes/registro-de-execucoes.md`, se for a primeira de uma funcionalidade ou se houve falha
- [ ] Falha encontrada → subtarefa `[BUG]` no Jira, com o rótulo `bug`
- [ ] `CONTEXT.md` atualizado, se alguma decisão ou convenção mudou
- [ ] Itens do Jira com o status certo
- [ ] Nenhum segredo ou dado pessoal (`.env`, senhas, CPF real)

**Merge:** *Squash and merge* por padrão (um commit por item na `main`); a branch é apagada depois.

## Versões

Entregas marcadas com tag na `main`, no formato [SemVer](https://semver.org/lang/pt-BR/) `vMAJOR.MINOR.PATCH`:

- `v0.x.y` durante o desenvolvimento do MVP; `v1.0.0` na primeira versão em produção.
- `MINOR` para funcionalidade nova; `PATCH` para correção.

```powershell
git tag -a v0.1.0 -m "Cadastro de usuários e lista de departamentos"
git push origin v0.1.0
```

## Proteção da `main` (GitHub)

Em *Settings → Branches → Branch protection rules*, para a `main`:

- exigir pull request antes do merge (com 0 aprovações enquanto só há um desenvolvedor back-end);
- quando houver CI, exigir que a verificação dos testes passe.

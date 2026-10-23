# CentralDesk — Guia de Desenvolvimento

Documento que **rege o desenvolvimento do CentralDesk a partir de 23/10/2026**. Vale para
back-end, front-end, banco, documentação e operação. Quando uma decisão conflitar com o que
está aqui, a divergência é apontada e resolvida antes de virar código.

| Documento | Papel |
|---|---|
| **este guia** | como trabalhamos: padrões, processo, *Definition of Done*, deploy |
| `CONTEXT.md` | **o que** o sistema é: estado, decisões tomadas, banco, endpoints, testes |
| `CONTRIBUTING.md` | mecânica do Git: branches, commits, pull requests, versões |
| `docs/testes/registro-de-execucoes.md` | rastreabilidade: toda execução de testes, inclusive as que falham |
| Jira (projeto `SCRUM`, *Innovation Unit*) | backlog, histórias, subtarefas, ADRs e decisões |

**Regra de ouro:** o `CONTEXT.md` do repositório é a fonte de verdade técnica, e a cópia
`Context.md` no conhecimento do Projeto (claude.ai) é um espelho dele. Os dois são atualizados
no mesmo PR que muda o comportamento. Os dois foram sincronizados com o código em
23/10/2026 (SCRUM-177); a partir daí, ficar defasado é descumprir a *Definition of Done*.

---

## 1. Papéis e idioma

- Único desenvolvedor back-end; coordenador da Inovação como *Product Owner*.
- O assistente (Claude) atua como **navegador do par**: propõe os testes primeiro, revisa,
  questiona e nunca decide sozinho o que estiver marcado como decisão em aberto.
- Respostas e documentação em **português**. Código, tabelas, colunas, classes e rotas em
  **inglês**. Na documentação ABNT, "chamado (denominado *ticket* no código-fonte)".

## 2. Padrões de projeto (os que o CentralDesk usa de fato)

Esta é a lista fechada. Padrão novo entra por decisão registrada (ADR no Jira), não por hábito.

### Arquitetura

- **Camadas:** `Controller (HTTP + DTO) → Service (regras, @Transactional) → Repository → banco`.
  O controller só traduz HTTP; o service **não conhece HTTP**.
- **DTO como `record`**, separados em entrada e saída, em `dto/`. Nunca expor entidade JPA;
  nunca devolver `password_hash` nem `cpf`.
- **Validação em duas alturas:** formato no DTO (Bean Validation, inclusive `@Cpf`,
  `@CorporateEmail`, `@StrongPassword`); regra que depende do banco, no service.
- **Tradução de exceções:** exceções de domínio em `exception/` → `GlobalExceptionHandler`
  (`@RestControllerAdvice`) → `ProblemDetail` (RFC 9457). Erro ligado a um campo estende
  `InvalidFieldException` e sai como 400 em `errors.<campo>`.
- **Documentação como interface:** os textos OpenAPI ficam em `controller/doc/*Api`, que o
  controller implementa. O controller fica só com mapeamento e chamada ao service.

### Domínio

- **Modelo rico / *tell, don't ask*:** `@Getter` nas entidades, **nunca `@Data` nem `@Setter`
  geral**; mudança de estado por método com intenção (`user.rename`, `ticket.close`,
  `departmentRequest.approve`).
- **Máquina de estados:** as transições válidas ficam em `TicketStatus.canTransitionTo` e o
  **único** ponto que troca o status é `Ticket.moveTo`. Não existe `setStatus`.
- **Serviço de apoio compartilhado:** o que duas operações têm em comum é extraído
  (`AccountOffboarding` para desativação/anonimização, `TicketAccess.canView` para
  visibilidade). Extração acontece na segunda ocorrência, não na primeira.
- **Concorrência por trava pessimista:** quem decide sobre um registro disputado lê com
  `findByIdForUpdate` (`PESSIMISTIC_WRITE`); o segundo a chegar recebe 409.

### Integração e infraestrutura

- **Eventos de domínio:** `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`.
  O efeito colateral (notificar, empurrar no SSE) só roda depois do commit e a falha dele não
  desfaz a operação.
- **Converter como ponto único de cifragem:** `EncryptedStringConverter` + `ContentCipher`
  (AES-256-GCM). Campo cifrado novo só precisa da anotação; a chave nunca sai da variável de ambiente.
- **Registry em memória para o SSE:** `SseEmitterRegistry` guarda um *stream* por usuário.
  Limite aceito: **uma única instância da aplicação** até existir pub/sub (SCRUM-51).
- **Bootstrap:** `AdminBootstrap` (`ApplicationRunner`) cria o primeiro admin a partir das
  variáveis `ADMIN_*`. Dado pessoal nunca entra em migration versionada.
- **Sem valores mágicos:** toda regra configurável vira chave `app.*` no `application.properties`,
  injetada por construtor.

### SOLID, Clean Code e YAGNI

- Um service por assunto; interface só onde existe (ou certamente existirá) mais de uma
  implementação. **Não** criar interface para service com implementação única.
- Injeção por construtor, nunca `@Autowired` em campo.
- Métodos curtos, um nível de abstração; nomes que revelam a intenção.
- Comentário explica o **porquê**, nunca o quê.
- Nada de abstração "para o futuro": o que fizer sentido depois vira trabalho futuro registrado.
- Soluções **proporcionais a um sistema interno de uma equipe de 3 pessoas**.

## 3. Banco de dados

- **Migration aplicada é imutável** — nem os comentários (o *checksum* muda). Mudança vira
  `V<n+1>__<descricao>.sql` (`V` maiúsculo, dois underlines).
- O **Flyway cria**, o Hibernate **confere** (`ddl-auto=validate` em desenvolvimento e CI).
- `snake_case` em inglês; `chk_<tabela>_<regra>` e `uq_<tabela>_<coluna>`; PK UUID com
  `gen_random_uuid()` no banco e `GenerationType.UUID` no Java; datas `TIMESTAMPTZ`.
- Valor fixo ligado ao código: `VARCHAR` + `CHECK` + `@Enumerated(STRING)`. Valor cadastrável:
  tabela própria com FK.
- **Soft delete:** usuário é desativado e depois anonimizado; FKs sem `ON DELETE CASCADE`.
  Exceção: notificações não têm valor histórico e são apagadas.
- **Expand/contract:** no mesmo release, só mudança aditiva. Remoção de coluna ou de valor
  entra num release posterior, depois que nenhuma versão em produção depende dela — é isso que
  permite voltar a imagem anterior sem voltar o banco.

## 4. Processo de trabalho

### Scrum e Jira

- Sprints de 1 a 2 semanas no projeto `SCRUM`; revisão ao fim de cada sprint.
- **Toda mudança tem issue antes do código.** Pendência combinada em conversa só existe de
  verdade depois de virar issue (`[RF…]`, `[FRONT]`, `[BUG]`, `OPS-`, `DEC-`, `DOC-`).
- Falha de teste vira **subtarefa `[BUG]`** com o rótulo `bug`.

### TDD (obrigatório)

1. **Vermelho:** escrever o teste unitário (JUnit 5 + Mockito, no service/validador) e o de
   integração (`@IntegrationTest` + Testcontainers, no endpoint) e rodar
   `.\mvnw test "-Dtest=NomeDaClasse"` confirmando que falha **pelo motivo certo**.
2. **Verde:** implementar o mínimo.
3. **Refatorar:** com a suíte verde.

Sem banco em memória: o comportamento precisa ser o do PostgreSQL. Teste de integração novo
usa `@IntegrationTest` e limpa com `DatabaseCleanup.removeNonAdminUsers` (toda tabela nova que
aponte para `users` entra na ordem).

### Branches, commits e PR

- `main` sempre estável; nada commitado direto nela (hooks em `.githooks/`;
  `git config core.hooksPath .githooks` em cada cópia nova).
- **Uma branch por item entregue de uma vez** (normalmente a subtarefa), no formato
  `tipo/SCRUM-N-descricao`: `feature/`, `fix/`, `hotfix/`, `refactor/`, `test/`, `docs/`,
  `chore/`, `spike/`.
- Branch nasce da `main` atualizada: `git switch main; git pull; git switch -c ...`.
- Conventional Commits em português, com `Refs: SCRUM-N`. **Os commits são feitos pelo
  desenvolvedor** (o assistente entrega o conteúdo, o resumo das alterações e a mensagem pronta).
- PR com *squash and merge*; versão na `main` com tag `vMAJOR.MINOR.PATCH`.

### Definition of Done (vale para toda história)

- [ ] Testes escritos **antes**, unitário e de integração, e verdes.
- [ ] Suíte completa rodada (`.\mvnw clean test`, Docker ligado) e **registrada** em
      `docs/testes/registro-de-execucoes.md` — comando, total, resultado e, nas falhas, causa
      raiz e correção.
- [ ] Endpoint novo documentado em `controller/doc/` (`@Operation` + um `@ApiResponse` por
      status, erros com `ProblemDetail` e exemplo em `ProblemExamples`) e coberto no
      `ApiDocsIntegrationTest` quando fizer sentido.
- [ ] `CONTEXT.md` atualizado (endpoints, estrutura, testes, pendências) **e** a cópia do
      Projeto espelhada.
- [ ] Issue do Jira movida, com o resumo das alterações no comentário.
- [ ] Branch própria, commits convencionais, PR aberto — sem push nem merge pelo assistente.
- [ ] Nada de dado pessoal, segredo ou chave em arquivo versionado.

### Segurança (não negociável)

- Senha com bcrypt (máx. 72 caracteres). Token de acesso JWT HS256 de 15 min, só em cookie
  `HttpOnly; Secure; SameSite=Strict`; a API não aceita `Authorization: Bearer`.
- Refresh token de 7 dias, guardado só como hash SHA-256, **rotacionado** a cada uso, com
  detecção de reuso que revoga todas as sessões.
- Autorização em **duas camadas**: regra por rota/método no `SecurityConfig` (ou `@PreAuthorize`)
  **e** checagem no service. O front apenas esconde opções — quem barra é a API.
- Mudança de e-mail de login ou de senha move `users.sessions_valid_from`: os tokens emitidos
  antes param de valer na hora (PR #26).
- Chave de conteúdo (`CONTENT_ENCRYPTION_KEY`) e chave de token (`JWT_SECRET`) são distintas
  por ambiente, ficam só em variável de ambiente e **a de conteúdo nunca muda** — sem ela, os
  comentários gravados não podem ser lidos.

## 5. Roteiro a partir de agora

Ordem combinada. O que está aqui não é reordenado sem decisão explícita.

1. **Dívida de produção** (pré-requisito do deploy automatizado — seção 6, passo 1).
2. **Núcleo das notificações (SCRUM-152):** entidade `Notification`, `NotificationType` com
   categoria, `NotificationCategory`, os quatro endpoints e o
   `DepartmentRequestNotificationListener` gravando de verdade. É o que falta para o
   contador/*dot* da Fila de atendimento funcionar.
3. **Eventos pendentes do painel:** SCRUM-153 (`TICKET_ASSIGNED`, `TICKET_CLOSED`),
   SCRUM-154 (`TICKET_COMMENTED`), SCRUM-155 (conta e admin).
4. **Tempo real confiável:** evento SSE para pedido de departamento e notificação, heartbeat
   periódico e reconexão; 1 instância até existir pub/sub.
5. **Responsividade do menu** (front).
6. **Painel de métricas e painel BI** (front + endpoints de agregação).
7. **Avaliação do atendimento (SCRUM-150)**, depois da decisão DEC-006 (SCRUM-151) sobre quem
   vê as notas.
8. **Faxinas pendentes:** limpeza periódica de `refresh_tokens` e de notificações lidas;
   `spring.jpa.open-in-view=false`; Mockito como agente do Surefire; diagramas e ABNT com
   `department_requests`, `ticket_ratings` e `notifications`.

## 6. Automação do deploy (produção no Railway)

Hoje: `Dockerfile` multi-estágio (build com JDK 21, execução com JRE, usuário sem privilégio,
`PORT` do Railway), testes **fora** da imagem (precisam de Docker, via Testcontainers) e deploy
a partir do GitHub. O que falta é o **portão**: hoje nada impede que um commit vermelho suba.

### Passo 1 — Pagar a dívida de produção (antes de automatizar)

Numa branch `chore/SCRUM-N-divida-de-producao`:

| # | Item | Por quê |
|---|---|---|
| 1.1 | Expor `/actuator/health` e liberá-lo no `PUBLIC_ENDPOINTS` (`management.endpoints.web.exposure.include=health`, `show-details=never`) | o actuator está no `pom.xml`, mas `/actuator/**` exige JWT: o healthcheck do Railway receberia 401 e o deploy seria marcado como falho |
| 1.2 | **CORS** com origem exata do front e `allowCredentials(true)` | front e API em origens diferentes: sem isso o navegador bloqueia toda chamada autenticada |
| 1.3 | **Domínio próprio** (`centraldesk.contabilizape.com.br` para o front, `api.centraldesk…` para a API) | `*.up.railway.app` são *sites* distintos para o navegador, e o cookie `SameSite=Strict` **não é enviado**. Subdomínios do mesmo domínio resolvem — e mantêm o CSRF coberto pelo `SameSite` |
| 1.4 | `spring.flyway.baseline-on-migrate=false` em produção | com `true`, um banco sem histórico "assume" que as migrations antigas já rodaram e sobe com schema incompleto |
| 1.5 | `ddl-auto=validate` em desenvolvimento e CI (hoje `none`) | divergência entre entidade e schema derruba na subida, não na primeira requisição |
| 1.6 | **Heartbeat no SSE** (comentário periódico no stream) | o proxy derruba conexão ociosa; sem heartbeat o tempo real cai em silêncio |
| 1.7 | Fixar **1 réplica** do back-end | o `SseEmitterRegistry` é em memória; duas instâncias entregam evento pela metade |

### Passo 2 — CI no GitHub Actions (o portão)

`.github/workflows/ci.yml`, disparado em `push` na `main` e em `pull_request`:

- `ubuntu-latest` (tem Docker, então o Testcontainers funciona), `actions/setup-java@v4` com
  `temurin` 21 e `cache: maven`.
- `./mvnw -B clean test`.
- Publicar o relatório do Surefire como artefato da execução.
- *Concurrency group* por branch **sem** `cancel-in-progress` na `main` (um run cancelado
  atrapalha o portão do Railway).

### Passo 3 — Ligar o portão no Railway

No serviço do back-end: **Settings → Wait for CI**. O deploy fica em `WAITING` até os workflows
do commit terminarem; falhou, o deploy é **descartado**. Requisitos: workflow que roda em `push`
na branch de deploy e as permissões atualizadas do GitHub aceitas. Branch de deploy = `main`.
Se usar *watch paths*, incluir `src/**`, `pom.xml`, `Dockerfile` e `src/main/resources/**`.

### Passo 4 — Healthcheck, política de reinício e configuração versionada

Healthcheck em `/actuator/health` com timeout folgado (a aplicação sobe o Flyway antes de
responder) e reinício `ON_FAILURE`. A configuração pode ficar no repositório:
`railway.json` ainda funciona para serviço já existente, mas **só até 01/12/2026** e serviço novo
não pode mais optar por ele — o caminho atual é *Infrastructure as Code* em
`.railway/railway.ts` (`healthcheck`, `preDeploy`, `env`, `domains`, por ambiente). Enquanto a
configuração é pequena, deixá-la no painel também é aceitável; o que não é aceitável é ela
existir só na cabeça de quem configurou.

### Passo 5 — Variáveis de ambiente por ambiente

- Banco, por **variável de referência** do serviço Postgres (host interno, sem egress):
  `DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`,
  `DB_USER=${{Postgres.PGUSER}}`, `DB_PASSWORD=${{Postgres.PGPASSWORD}}`.
- `JWT_SECRET` e `CONTENT_ENCRYPTION_KEY`: base64 de 32 bytes, **novos e distintos** por
  ambiente, com cópia da chave de conteúdo em cofre de senhas.
- `COOKIE_SECURE=true`. `API_DOCS_ENABLED` e `SHOW_SQL`: **ausentes** em produção.
- `ADMIN_*`: só até o primeiro administrador existir; depois, remover do ambiente.
- `PORT` é do Railway e já está tratado no `application.properties`.

### Passo 6 — Ambientes e front-end

- Dois ambientes no Railway: `production` (branch `main`) e `staging` (branch de release), cada
  um com seu Postgres e suas chaves. Nada é testado pela primeira vez em produção.
- Front-end: build estático em serviço próprio, com `VITE_API_URL` por ambiente e o mesmo
  portão de CI (lint + build + testes).

### Passo 7 — Migrations no deploy

O Flyway roda na subida da aplicação, o que basta com **uma** instância. Quando houver mais de
uma, as migrations passam para `preDeploy` (roda uma vez, antes de trocar a versão). A regra
*expand/contract* da seção 3 é o que mantém o rollback possível.

### Passo 8 — Release, verificação e rollback

1. PR verde → *squash and merge* na `main`.
2. CI passa → Railway builda e sobe → healthcheck verde.
3. *Smoke test* (job final do workflow ou script manual): `GET /actuator/health`, `POST /auth/login`
   com usuário de teste e `GET /users/me`.
4. Tag `vMAJOR.MINOR.PATCH` na `main`, com o resumo do release.
5. **Rollback** = *Redeploy* da versão anterior no Railway. O banco não volta: por isso
   migration aditiva.
6. **Backup**: backup gerenciado do Postgres do Railway **mais** um `pg_dump` semanal guardado
   fora do Railway, com restauração testada uma vez por trimestre.

Cada passo acima vira uma issue `OPS-` no Jira antes de ser executado.

## 7. Orientações específicas para o assistente

- Ler o `CONTEXT.md` e o `CONTRIBUTING.md` antes de propor código; explorar o que já existe e
  **reaproveitar** (helpers de teste, `TicketAccess`, `AccountOffboarding`, `ProblemExamples`).
- Comandos em **PowerShell** (`.\mvnw`), não bash.
- Entregar a **classe inteira** (package, imports, campos, construtor), não trechos.
- Testes primeiro, sempre; e explicar o porquê de cada escolha, porque isso alimenta a
  documentação ABNT.
- **Parar e perguntar** diante de decisão em aberto, de conflito com o `CONTEXT.md` ou de
  qualquer mudança em decisão de segurança, em migration já aplicada ou em dado de produção.
- Não fazer push nem merge. Ao fim de cada história: branch, commits, arquivos, testes novos,
  total da suíte, decisões tomadas e perguntas em aberto.

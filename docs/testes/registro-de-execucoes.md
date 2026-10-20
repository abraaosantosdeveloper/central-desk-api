# Registro de execuções de testes

Histórico das execuções da suíte de testes do CentralDesk, **incluindo as que falharam**. Serve para rastrear quando cada defeito apareceu, qual foi a causa e qual execução confirmou a correção.

## Como registrar

- Toda execução relevante entra aqui: a primeira de uma funcionalidade nova, qualquer execução com falha e a execução que confirma uma correção.
- Falhas viram uma subtarefa `[BUG]` no Jira, com o rótulo `bug`, na história da funcionalidade. O número da subtarefa é anotado aqui.
- Os relatórios brutos ficam em `target/surefire-reports/`. Eles não vão para o Git, por isso o resumo fica neste arquivo.

Modelo de entrada:

```
## Execução N — AAAA-MM-DD HH:MM — <escopo>
- Comando:
- Resultado: X testes, Y falhas, Z erros
- Falhas: <teste> — <mensagem>
- Causa raiz:
- Correção:
- Rastreio: SCRUM-___
- Confirmada na execução: N+1
```

---

## Execução 1 — 2026-10-16 ~09:26 — `CpfValidatorTest`

- **Comando:** `.\mvnw test "-Dtest=CpfValidatorTest"`
- **Resultado:** 16 testes, 0 falhas. ✅
- **Observação:** o teste foi escrito antes do `CpfValidator` (TDD), mas a implementação foi criada antes da primeira execução, então o vermelho inicial (erro de compilação) não foi registrado.
- **Rastreio:** SCRUM-126

## Execução 2 — 2026-10-16 09:43 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 52 testes, **1 falha**, 0 erros. ❌ (23 s)

| Classe | Testes | Situação |
|---|---|---|
| `CentralDeskApplicationTests` | 1 | ✅ |
| `AuthControllerIntegrationTest` | 8 | ❌ 1 falha |
| `UserCreateRequestValidationTest` | 16 | ✅ |
| `UserServiceTest` | 11 | ✅ |
| `CpfValidatorTest` | 16 | ✅ |

- **Falha:** `AuthControllerIntegrationTest.rejectsInvalidFieldsListingEachOne:114` — `No value at JSON path "$.errors.cpf"`.
- **Sintoma:** a resposta foi 400, mas no formato padrão do Spring, sem `errors`:
  `{"detail":"Invalid request content.","instance":"/auth/register","status":400,"title":"Bad Request"}`
- **Causa raiz:** `spring.mvc.problemdetails.enabled=true` registra o `ProblemDetailsExceptionHandler` do Spring Boot, que também trata `MethodArgumentNotValidException` e foi escolhido antes do nosso `GlobalExceptionHandler`.
- **Correção:** o `GlobalExceptionHandler` passou a estender `ResponseEntityExceptionHandler` (o Spring Boot deixa de registrar o dele) e a sobrescrever `handleMethodArgumentNotValid`. A propriedade foi removida.
- **Rastreio:** SCRUM-133
- **Confirmada na execução:** 3 ✅

### Mudanças entre a execução 2 e a 3

- Correção da SCRUM-133 (acima).
- Validação do domínio do e-mail movida do `UserService` para a anotação `@CorporateEmail` no DTO:
  - novo `CorporateEmailValidatorTest` (9 testes);
  - `UserCreateRequestValidationTest`: +2 testes de domínio;
  - `UserServiceTest`: −2 testes de domínio;
  - `AuthControllerIntegrationTest`: o caso de outro domínio passou a esperar `errors.email`.
- Total esperado na execução 3: **61 testes**.

## Execução 3 — 2026-10-16 09:52 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 61 testes, 0 falhas, 0 erros. ✅ (27 s; Java 25.0.4.1, Testcontainers 2.0.5, PostgreSQL 16.15, Docker Desktop 29.8.2)

| Classe | Testes | Situação |
|---|---|---|
| `CentralDeskApplicationTests` | 1 | ✅ |
| `AuthControllerIntegrationTest` | 8 | ✅ (inclui `rejectsInvalidFieldsListingEachOne`, que falhou na execução 2) |
| `UserCreateRequestValidationTest` | 18 | ✅ |
| `UserServiceTest` | 9 | ✅ |
| `CorporateEmailValidatorTest` | 9 | ✅ |
| `CpfValidatorTest` | 16 | ✅ |

- **Confirma:** a correção da SCRUM-133 e o `@CorporateEmail` (SCRUM-126).
- **Evidências adicionais no log:** as 9 migrations foram aplicadas num banco vazio, o Hibernate validou o mapeamento (`ddl-auto=validate`) e o ouvinte registrou `Notificar abraao.inovacao@...` para o admin da V8 no cenário da Inovação.
- **Avisos no log (não são falhas):**
  - `Using generated security password`: o Spring cria um usuário em memória porque ainda não existe `UserDetailsService`. Some quando o login for implementado.
  - `spring.jpa.open-in-view is enabled by default`: recomendável desligar (`spring.jpa.open-in-view=false`), para que nenhuma consulta ao banco aconteça fora da camada de service.
  - `Mockito is currently self-attaching`: nos JDKs futuros, o Mockito vai precisar ser configurado como agente do Surefire no `pom.xml`.
  - Dois contêineres do Postgres sobem por execução (um por contexto de teste: com e sem `@AutoConfigureMockMvc`). Aceitável por enquanto.


### Mudanças entre a execução 3 e a 4

- **V8 sem dados pessoais:** renomeada para `V8__seed_departments.sql`, cria só Inovação e Geral. O primeiro admin passou a ser criado pelo `AdminBootstrap` ao subir a aplicação, a partir das variáveis `ADMIN_*` (SCRUM-134).
  - Por isso, nos contextos de teste sem `ADMIN_*`, **não existe admin**: o cenário da Inovação no `AuthControllerIntegrationTest` deixa de registrar a linha `Notificar ...` (comportamento esperado).
- Novos testes:
  - `AdminBootstrapTest` (7, Mockito): não faz nada se já existe admin ou se faltam variáveis; cria na Inovação com hash e `granted_by_id` nulo; recusa credenciais inválidas e e-mail ou CPF já cadastrados; falha se a Inovação não existir.
  - `AdminBootstrapIntegrationTest` (2, Postgres real): o admin é criado ao subir e não duplica numa segunda execução.
- Total esperado na execução 4: **70 testes**.

## Execução 4 — 2026-10-16 10:24 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 70 testes, 0 falhas, **1 erro**. ❌ (27 s)

| Classe | Testes | Situação |
|---|---|---|
| `AdminBootstrapIntegrationTest` | 2 | ✅ (log: `Administrador inicial criado: ana.admin@contabilizape.com.br`) |
| `AdminBootstrapTest` | 7 | ❌ 1 erro |
| `CentralDeskApplicationTests` | 1 | ✅ (log: aviso de que não há `ADMIN_*`, como esperado) |
| `AuthControllerIntegrationTest` | 8 | ✅ |
| `UserCreateRequestValidationTest` | 18 | ✅ |
| `UserServiceTest` | 9 | ✅ |
| `CorporateEmailValidatorTest` | 9 | ✅ |
| `CpfValidatorTest` | 16 | ✅ |

- **Erro:** `AdminBootstrapTest.validatesCredentialsWithTheSameRulesAsRegistration` — `UnfinishedStubbingException`.
- **Causa raiz:** defeito no **código do teste**, não da aplicação. O auxiliar `violation(...)`, que configura mocks com `when(...)`, era chamado dentro do `thenReturn(...)` de outro `when(...)`. O Mockito não permite iniciar uma configuração de mock enquanto outra está em aberto.
- **Correção:** a violação passou a ser criada numa variável antes do `when(...)`.
- **Lição:** nunca chamar auxiliares que configuram mocks dentro de `thenReturn(...)`.
- **Rastreio:** SCRUM-135
- **Confirmada na execução:** 5 ✅

## Execução 5 — 2026-10-16 10:29 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 70 testes, 0 falhas, 0 erros. ✅ (28 s)

| Classe | Testes | Situação |
|---|---|---|
| `AdminBootstrapIntegrationTest` | 2 | ✅ |
| `AdminBootstrapTest` | 7 | ✅ (inclui `validatesCredentialsWithTheSameRulesAsRegistration`, com erro na execução 4) |
| `CentralDeskApplicationTests` | 1 | ✅ |
| `AuthControllerIntegrationTest` | 8 | ✅ |
| `UserCreateRequestValidationTest` | 18 | ✅ |
| `UserServiceTest` | 9 | ✅ |
| `CorporateEmailValidatorTest` | 9 | ✅ |
| `CpfValidatorTest` | 16 | ✅ |

- **Confirma:** a correção da SCRUM-135 e o `AdminBootstrap` com a V8 sem dados pessoais (SCRUM-134).
- **Evidências no log:** a V8 foi aplicada como `8 - seed departments`; `Administrador inicial criado: ana.admin@contabilizape.com.br` no contexto com `ADMIN_*`; aviso de ausência de `ADMIN_*` nos demais contextos, como esperado.

### Mudanças entre a execução 5 e a 6

- `application.properties` passou a importar o `.env` (`spring.config.import=optional:file:.env[.properties]`). Motivo: com as `ADMIN_*` só no `.env`, o admin não era criado quando a aplicação subia por um caminho que não lê o arquivo.
- Efeito colateral evitado: como os testes rodam na raiz do projeto, passariam a ler o `.env` local e a criar o admin real. `CentralDeskApplicationTests` e `AuthControllerIntegrationTest` agora zeram `app.bootstrap.admin.*` nas propriedades do teste (o `AdminBootstrapIntegrationTest` já define as suas).
- Total esperado na execução 6: 70 testes (nenhum teste novo).

## Execução 6 — 2026-10-16 ~10:58 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 70 testes, 0 falhas, 0 erros. ✅ (resultado informado pelo desenvolvedor, sem log anexado)
- **Confirma:** importar o `.env` pelo `application.properties` não afetou os testes, porque os contextos de teste zeram `app.bootstrap.admin.*`.
- **Validação manual no ambiente local:** com as `ADMIN_*` no `.env`, o admin foi criado ao subir a aplicação (SCRUM-134). Achado: um nome acentuado chegou corrompido ("Ã"), porque o `.env` importado é lido em ISO-8859-1. Correção: escape Unicode no `.env` e `UPDATE` do registro (documentado no `CONTEXT.md` e no `.env.example`).

### Mudanças entre a execução 6 e a 7

- `GET /departments` (SCRUM-125) e a liberação dele no `SecurityConfig` (SCRUM-124).
- Novos testes:
  - `DepartmentServiceTest` (2, Mockito): lista ordenada por nome e lista vazia;
  - `DepartmentControllerIntegrationTest` (4): os departamentos da V8 são acessíveis sem login, a ordem é por nome, a resposta traz só `id` e `name`, e o `POST /departments` continua bloqueado (401/403, e não 405).
- Os mocks do `DepartmentServiceTest` são criados antes do `when(...)`, aplicando a lição da execução 4.
- O `DepartmentControllerIntegrationTest` usa as mesmas configurações do `AuthControllerIntegrationTest`, então reaproveita o mesmo contexto e o mesmo contêiner.
- Total esperado na execução 7: **76 testes**.


## Execução 7 — 2026-10-16 ~11:02 — suíte completa

- **Comando:** `.\mvnw test`
- **Resultado:** 76 testes, 0 falhas, 0 erros. ✅ (resultado informado pelo desenvolvedor, sem log anexado)
- **Confirma:** `GET /departments` público e ordenado (SCRUM-125) e a liberação só do método GET no `SecurityConfig` (SCRUM-124).

### Mudanças entre a execução 8 e a 9

- Login (SCRUM-137): JWT HS256 pelo OAuth2 Resource Server, token no cookie `access_token`, `POST /auth/login` e `GET /users/me`.
- `@IntegrationTest` passa a fixar uma chave JWT de teste (`IntegrationTest.JWT_SECRET`) e `app.security.cookie.secure=true`. Os testes não dependem do `JWT_SECRET` do `.env`.
- `AdminBootstrapIntegrationTest` passa a usar `@IntegrationTest` + `@TestPropertySource` com as `ADMIN_*`. Assim ele também recebe a chave JWT, sem a qual o contexto não sobe.
- `SecurityConfig`: a liberação de `/auth/**` virou a lista explícita `PUBLIC_ENDPOINTS`. `POST /departments` continua bloqueado, mas agora responde 401 (resource server) em vez de 403; o teste aceita os dois.
- Novos testes (41):
  - `JwtConfigTest` (3), `TokenServiceTest` (5), `CookieBearerTokenResolverTest` (5): unitários, sem Spring;
  - `AuthServiceTest` (8, Mockito): os auxiliares que configuram mocks são chamados antes do `when(...)` (lição da execução 4);
  - `UserServiceTest` (+3): `getCurrentUser`;
  - `AuthLoginIntegrationTest` (8) e `UserControllerIntegrationTest` (7);
  - `ApiDocsIntegrationTest` (+2): login e `/users/me` documentados, esquema `cookieAuth`.
- Total esperado na execução 9: **124 testes**.

## Execução 9 — 2026-10-16 ~13:25 — suíte completa

- **Comando:** `.\mvnw clean test`
- **Resultado:** 124 testes, 0 falhas, 0 erros. ✅ (resultado informado pelo desenvolvedor, sem log anexado)
- **Confirma:** emissão e validação do JWT (SCRUM-138), `POST /auth/login` (SCRUM-139), `GET /users/me` (SCRUM-140) e a documentação dos dois no Swagger (SCRUM-141). Também confirma que a troca de `/auth/**` por `PUBLIC_ENDPOINTS` não quebrou o cadastro nem a lista de departamentos.
- **Validação manual:** no Swagger, a primeira tentativa de login respondeu 401. Foi resolvida pelo desenvolvedor sem mudança no código, e os testes de integração do login passam. Em seguida, o login e o `GET /users/me` funcionaram com o cookie.

### Mudanças entre a execução 9 e a 10

- Logout (SCRUM-142): `POST /auth/logout` responde 204 e apaga o cookie (`AccessTokenCookie.clear()`); a rota entrou em `PUBLIC_ENDPOINTS`.
- `AccessTokenCookie`: os atributos de proteção ficaram num único método, usado tanto pelo cookie criado quanto pelo apagado.
- Novos testes (6):
  - `AccessTokenCookieTest` (2): atributos do cookie criado e do apagado;
  - `AuthLogoutIntegrationTest` (3): logout com sessão, sem cookie e com cookie inválido;
  - `ApiDocsIntegrationTest` (+1): logout documentado.
- Total esperado na execução 10: **130 testes**.

## Execução 10 — 2026-10-16 ~13:44 — suíte completa

- **Comando:** `.\mvnw clean test`
- **Resultado:** 130 testes, 0 falhas, 0 erros. ✅ (resultado informado pelo desenvolvedor, sem log anexado)
- **Confirma:** `POST /auth/logout` público e idempotente, que apaga o cookie com sessão, sem cookie ou com cookie inválido (SCRUM-142).

### Mudanças entre a execução 10 e a 11

- Refresh token com rotação (SCRUM-143): entidade `RefreshToken` (tabela `refresh_tokens` da V7), `RefreshTokenService`, cookie `refresh_token` (`Path=/auth`), `POST /auth/refresh`; o login emite os dois cookies e o logout revoga a sessão e apaga os dois.
- **Limpeza dos testes**: com sessões gravadas em `refresh_tokens` (chave estrangeira para `users`), apagar usuários sem apagar antes as sessões violaria a FK. As quatro classes que limpavam o banco passaram a usar `DatabaseCleanup.removeNonAdminUsers`, que apaga na ordem certa.
- `AuthLogoutIntegrationTest`: passa a conferir que os **dois** cookies são apagados, cada um com o seu `Path`.
- Novos testes (27):
  - `RefreshTokenServiceTest` (10) e `RefreshTokenCookieTest` (2): unitários;
  - `AuthServiceTest` (+3): renovação com permissões recalculadas e logout com e sem token;
  - `AuthRefreshIntegrationTest` (11): rotação, só o hash no banco, reuso derruba todas as sessões, sem cookie, token desconhecido, vencido, conta inativa, access token vencido não atrapalha, papel novo aparece na renovação e logout revoga;
  - `ApiDocsIntegrationTest` (+1): refresh documentado.
- Total esperado na execução 11: **157 testes**.

### Mudanças entre a execução 11 e a 12

- Abertura de chamados (SCRUM-145):
  - entidades `Ticket`, `Category` e `Priority`, com `V10__seed_categories.sql`;
  - `TicketService`, `TicketCatalogService` e os endpoints `POST /tickets`, `GET /categories` e `GET /priorities`.
- `DatabaseCleanup` passa a apagar `comments` e `tickets` antes de `users`, por causa das chaves estrangeiras.
- Novos testes (14):
  - `TicketServiceTest` (5, Mockito): a categoria e a prioridade são mocks criados fora do `when(...)`;
  - `TicketControllerIntegrationTest` (8): abertura, solicitante e status vindos do servidor, sem sessão, campos inválidos, categoria ou prioridade inexistente, listas ordenadas, catálogo exige sessão;
  - `ApiDocsIntegrationTest` (+1).
- Total esperado na execução 12: **171 testes**.

## Execução 11 — refresh token (SCRUM-143)

- **Resultado:** o desenvolvedor não informou esta execução separadamente; o merge do PR #7 foi feito e a execução 12 roda a suíte inteira, com o refresh token incluído.

## Execução 12 — 2026-10-16 ~14:16 — suíte completa

- **Comando:** `.\mvnw clean test`
- **Resultado:** 171 testes, 0 falhas, 0 erros. ✅ (resultado informado pelo desenvolvedor, sem log anexado)
- **Confirma:** abertura de chamados, categorias e prioridades (SCRUM-145) e, de tabela, o refresh token com rotação (SCRUM-143).

### Mudanças entre a execução 12 e a 13

- Edição de chamado (SCRUM-146): `PUT /tickets/{id}`, `Ticket.edit()` e `Ticket.isOpen()`; exceções `TicketNotFoundException` (404), `TicketEditForbiddenException` (403) e `TicketNotEditableException` (409).
- Novos testes (11):
  - `TicketServiceTest` (+5): edição pelo solicitante, chamado inexistente, outro usuário, chamado já assumido e categoria inexistente;
  - `TicketControllerIntegrationTest` (+6): 200, 403, 409, 404, 401 e 400;
  - `ApiDocsIntegrationTest`: o teste de chamados passou a conferir também o `PUT`.
- Total esperado na execução 13: **182 testes**.


### Mudanças entre a execução 12 e a 14

- (A execução 13, da SCRUM-146, foi registrada pelo desenvolvedor fora deste arquivo: 182 testes esperados.)
- Edição dos dados do usuário (SCRUM-147): `PUT /users/me` (só o nome) e `PATCH /users/me/password` (204 com cookies novos; todas as sessões revogadas).
- `@StrongPassword` (anotação composta) passa a ser usada no cadastro e na troca de senha; `InvalidFieldException` vira 400 com `errors.<campo>`.
- Novos testes (25):
  - `UserServiceTest` (+3): renomeia, usuário apagado, conta desativada;
  - `AuthServiceTest` (+5): troca com sucesso, senha atual errada, senha igual, usuário apagado, conta desativada;
  - `RefreshTokenServiceTest` (+1): revogar todas as sessões;
  - `PasswordChangeRequestValidationTest` (6, novo);
  - `UserControllerIntegrationTest` (+9): PUT 200/400/401/403 (campos extras ignorados); PATCH 204 + outra sessão não renova + login só com a nova, 400 senha atual, 400 igual, 400 fraca, 401;
  - `ApiDocsIntegrationTest` (+1).

## Execução 14 — 2026-10-20 — suíte completa (SCRUM-147)

- **Comando:** `.\mvnw clean test` (Docker ligado)
- **Resultado:** 207 testes, 0 falhas, 0 erros. ✅
- **Antes (TDD):** a execução direcionada das classes acima falhou pelo motivo esperado (rotas 404/405 e `UnsupportedOperationException` nos stubs). Um teste meu falhou com `UnfinishedStubbing` (mock criado dentro do `thenReturn`); corrigido no próprio teste.

### Mudanças entre a execução 14 e a 15

- Encerramento de conta (SCRUM-159): `DELETE /users/me` (próprio usuário, confirma a senha) e `PATCH /users/{id}/anonymize` (admin, só conta `DEACTIVATED`).
- `DatabaseCleanup` passa a apagar `notifications` e `ticket_ratings` antes de `comments`.
- Novos testes (21):
  - `AccountAnonymizationServiceTest` (10, novo);
  - `AccountAnonymizationIntegrationTest` (10, novo): anonimização completa (dados, sessões, fila, notificações, cookies, CPF e e-mail liberados), senha errada, sem senha, sem cookie, último admin, admin anonimiza desativada, conta ativa → 409, inexistente → 404, não admin → 403, sem cookie → 401;
  - `ApiDocsIntegrationTest` (+1).

## Execução 15 — 2026-10-20 — suíte completa (SCRUM-159)

- **Comando:** `.\mvnw clean test` (Docker ligado)
- **Resultado:** 228 testes, 0 falhas, 0 erros. ✅
- **Antes (TDD):** as duas classes novas falharam pelo motivo esperado (rotas 404/405 e `UnsupportedOperationException` no service).

### Mudanças entre a execução 15 e a 16

- Detalhes do chamado (SCRUM-161): `GET /tickets/{id}`; `TicketResponse` com responsável, encerramento e `updatedAt`.
- Novos testes (12): `TicketServiceTest` (+6: solicitante, handler, admin, outro colaborador → 404, inexistente, conta desativada); `TicketControllerIntegrationTest` (+6: 200 solicitante/handler/admin, 404 outro, 404 inexistente, 401). `ApiDocsIntegrationTest`: o teste de chamados confere também o `GET`.

## Execução 16 — 2026-10-20 — suíte completa (SCRUM-161)

- **Comando:** `.\mvnw clean test` (Docker ligado)
- **Antes (TDD):** execução direcionada falhou pelo motivo esperado (405 e `UnsupportedOperationException`).
- **Primeira execução completa:** falhou na compilação — `unknownTicketIsNotFound()` já existia em `TicketServiceTest` e `TicketControllerIntegrationTest` (a execução direcionada anterior tinha usado classes compiladas antigas). **Correção:** os testes novos foram renomeados para `unknownTicketDetailsAreNotFound`.
- **Resultado:** 240 testes, 0 falhas, 0 erros. ✅

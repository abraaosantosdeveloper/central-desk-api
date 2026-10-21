# CentralDesk

Painel web para registro e acompanhamento de chamados internos da Contabiliza, em substituição ao atendimento por e-mail.

## Problema

Hoje os chamados são registrados por e-mail, o que dificulta a organização, a priorização por urgência e o acompanhamento do andamento, tornando o atendimento lento e ineficiente.

## Solução

Um painel onde os colaboradores registram seus chamados, classificados por **categoria**, **prioridade** e **solicitante**, e onde o departamento de Inovação os atende. O sistema notifica os envolvidos por **e-mail** e dentro da **interface**.

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Front-end | Vue.js |
| Back-end | Java 21 + Spring Boot 4.1.1 (Spring Security, Spring Data JPA) |
| Banco de dados | PostgreSQL 16 (Docker), administrado com pgAdmin |
| Migrations | Flyway |
| Autenticação | JWT em cookie `HttpOnly` + refresh token com rotação (guardado como hash); senhas com bcrypt |
| Identificadores | UUID |
| Documentação da API | OpenAPI 3 + Swagger UI (springdoc) |

## Requisitos funcionais

**Contas**

- **RF00**: criação de conta
- **RF01**: login
- **RF02**: edição dos dados do usuário
- **RF03**: logout
- **RF04**: encerramento de conta (anonimização, sem exclusão física)

**Chamados**

- **RF05**: abertura de chamados
- **RF06**: alteração de status (Em aberto, Em progresso, Encerrado)
- **RF07**: encerramento de chamados
- **RF08**: categorização no momento da abertura
- **RF09**: definição de urgência
- **RF10**: associação a setor e colaborador responsável
- **RF11**: listagem com filtros por status, categoria, urgência, setor e colaborador
- **RF12**: consulta aos detalhes e à última alteração do chamado
- **RF13**: comentários nos chamados
- **RF14**: notificações por e-mail
- **RF15**: notificações internas na interface

## Pré-requisitos

- Java 21 ou superior
- Docker e Docker Compose
- Node.js e npm (front-end)

O Maven não precisa ser instalado: o projeto usa o Maven Wrapper (`mvnw`).

## Como executar

0. Ative os hooks do Git, que bloqueiam commit e push direto na `main` (ver [`CONTRIBUTING.md`](CONTRIBUTING.md)):

   ```powershell
   git config core.hooksPath .githooks
   ```

1. Copie `.env.example` para `.env` e preencha as credenciais:

   ```env
   DB_URL=jdbc:postgresql://localhost:5432/chamados
   DB_USER=
   DB_PASSWORD=
   PGADMIN_EMAIL=
   PGADMIN_PASSWORD=

   # Primeiro administrador (criado ao subir a aplicação, se ainda não houver nenhum)
   ADMIN_NAME=
   ADMIN_CPF=
   ADMIN_EMAIL=
   ADMIN_PASSWORD=
   ```

   As variáveis `ADMIN_*` substituem qualquer dado pessoal em migration: o e-mail precisa ser `@contabilizape.com.br`, o CPF válido (só dígitos) e a senha com 8 a 72 caracteres, letra e número.

2. Suba o banco e o pgAdmin:

   ```powershell
   docker compose up -d
   ```

   - Banco: `localhost:5432`, banco `chamados`
   - pgAdmin: `http://localhost:5050` (ao registrar o servidor, use o host `db`)

3. Gere a chave dos tokens de login e coloque no `.env` como `JWT_SECRET=<valor>` (sem ela, a aplicação não sobe):

   ```powershell
   $b = New-Object byte[] 32; $rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($b); $rng.Dispose(); [Convert]::ToBase64String($b)
   ```

   No Linux, macOS ou WSL: `openssl rand -base64 32`.

   Rode o mesmo comando de novo e coloque o resultado como `CONTENT_ENCRYPTION_KEY=<valor>`: é a chave que cifra os comentários dos chamados (AES-256-GCM). Ela fica **só** no `.env` (em produção, nas variáveis de ambiente do servidor), nunca no banco. Sem ela a aplicação não sobe, e **se ela for perdida os comentários já gravados não podem mais ser lidos**: guarde uma cópia num cofre de senhas.

4. Inicie o back-end, na raiz do projeto:

   ```powershell
   .\mvnw clean spring-boot:run
   ```

   No Linux, macOS ou WSL: `./mvnw clean spring-boot:run`. O Flyway aplica as migrations ao subir, e a API fica em `http://localhost:8080`.

5. Explore a API no Swagger UI: `http://localhost:8080/swagger-ui.html`. Ele só existe com `API_DOCS_ENABLED=true` no `.env` (já vem assim no `.env.example`); sem a variável, como em produção, as rotas da documentação respondem 404.

## Estrutura do projeto

```
.
├── src/main/java/br/com/contabilizape/centraldesk/
│   ├── config/          # segurança e configurações
│   ├── controller/      # endpoints HTTP
│   ├── dto/             # objetos de entrada e saída da API
│   ├── model/           # entidades JPA
│   ├── notification/    # eventos e notificações
│   ├── repository/      # acesso ao banco
│   └── service/         # regras de negócio
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/    # migrations do Flyway (V1..V9)
├── docs/                # documentação ABNT (PDF)
├── docker-compose.yaml  # PostgreSQL e pgAdmin
├── CONTEXT.md           # decisões e estado do projeto
├── .env.example
└── README.md
```

## Rotas da API

| Método | Rota | Finalidade | Situação |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | Cadastrar novo usuário (público) | implementada |
| `GET` | `/departments` | Listar departamentos para o cadastro (público) | implementada |
| `POST` | `/auth/login` | Iniciar sessão (token em cookie `HttpOnly`) | implementada |
| `POST` | `/auth/refresh` | Renovar a sessão (rotação do refresh token) | implementada |
| `POST` | `/auth/logout` | Encerrar sessão (revoga o refresh token e apaga os cookies) | implementada |
| `GET` | `/users/me` | Perfil do usuário logado | implementada |
| `PUT` | `/users/me` | Editar o próprio perfil | prevista |
| `PATCH` | `/users/me/password` | Trocar a senha | prevista |
| `GET` | `/users/{id}` | Consultar outro usuário (administrador) | prevista |
| `PATCH` | `/users/{id}/deactivate` | Desativar conta (administrador) | prevista |
| `GET` | `/department-requests` | Fila de pedidos de departamento (administrador) | prevista |
| `PATCH` | `/department-requests/{id}/approve` | Aprovar pedido (administrador) | prevista |
| `PATCH` | `/department-requests/{id}/reject` | Recusar pedido (administrador) | prevista |
| `POST` | `/tickets` | Abrir chamado | implementada |
| `PUT` | `/tickets/{id}` | Editar o próprio chamado enquanto ninguém o assumiu | implementada |
| `GET` | `/categories` | Categorias do formulário de chamado | implementada |
| `GET` | `/priorities` | Prioridades do formulário de chamado | implementada |
| `PATCH` | `/tickets/{id}/assign` | Assumir chamado | prevista |
| `PATCH` | `/tickets/{id}/close` | Encerrar chamado | prevista |

As rotas de autenticação ficam sob `/auth`; a ação vem do método HTTP, não da URL. A documentação completa de cada rota (campos, exemplos e respostas de erro) está no Swagger UI. Detalhes e regras de negócio estão no [`CONTEXT.md`](CONTEXT.md).

## Status

Back-end em desenvolvimento: cadastro de conta, login, renovação de sessão, logout, lista de departamentos, abertura de chamados e documentação no Swagger implementados. O andamento é acompanhado no Jira (projeto **Innovation Unit**, chave `SCRUM`).

## Como contribuir

Branches, commits, pull requests e versões estão descritos em [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Documentação

A documentação formal, em LaTeX seguindo as normas ABNT, está em `docs/DOC_SISTEMA_CHAMADOS_0.1.pdf`.

# Sistema de Gestão de Chamados

Painel web para registro e acompanhamento de chamados internos, em substituição ao atendimento por e-mail.

## Problema

Hoje os chamados são registrados por e-mail, o que dificulta a organização, a priorização por urgência e o acompanhamento do andamento, tornando o atendimento lento e ineficiente.

## Solução

Um painel onde os usuários registram seus chamados, classificados por **categoria**, **urgência**, **setor** e **colaborador**. O sistema notifica os envolvidos por **e-mail do domínio da empresa** e também dentro da **interface** do software.

## Tecnologias

| Camada | Tecnologia |
| --- | --- |
| Linguagens | JavaScript, Java e SQL |
| Front-end | Vue.js |
| Back-end | Spring Boot |
| Banco de dados | PostgreSQL (Docker), administrado com pgAdmin |
| Autenticação | Tokens em cookies HttpOnly, senhas com hash bcrypt |
| Identificadores | UUID |

## Requisitos funcionais

**Contas**

- **RF00**: criação de conta
- **RF01**: login
- **RF02**: edição dos dados do usuário
- **RF03**: logout
- **RF04**: encerramento de conta (anonimização, sem exclusão física)

**Chamados**

- **RF05**: abertura de chamados
- **RF06**: alteração de status (Em aberto, Em progresso, Concluído/Encerrado)
- **RF07**: encerramento de chamados

**Propostos (a validar)**

- **RF08**: categorização no momento da abertura
- **RF09**: definição de urgência
- **RF10**: associação a setor e colaborador responsável
- **RF11**: listagem com filtros por status, categoria, urgência, setor e colaborador
- **RF12**: consulta aos detalhes e ao histórico do chamado
- **RF13**: comentários nos chamados
- **RF14**: notificações por e-mail do domínio da empresa
- **RF15**: notificações internas na interface

## Pré-requisitos

- Java 21 ou superior
- Maven
- Node.js e npm
- Docker e Docker Compose

## Como executar

1. Copie `.env.example` para `.env` e preencha as credenciais:

   ```env
   DB_USER=
   DB_PASSWORD=
   PGADMIN_EMAIL=
   PGADMIN_PASSWORD=
   ```

2. Suba o banco e o pgAdmin:

   ```bash
   docker compose up -d
   ```

   - Banco: `localhost:5432`
   - pgAdmin: `http://localhost:5050` (ao registrar o servidor, use o host `db`)

3. Inicie o back-end:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

4. Inicie o front-end:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## Estrutura do projeto

```
.
├── backend/              # API Spring Boot
├── frontend/             # Aplicação Vue.js
├── docker-compose.yml    # PostgreSQL e pgAdmin
├── .env.example
└── README.md
```

## Status

Em fase de levantamento de requisitos e configuração do ambiente.

## Documentação

A documentação formal, em LaTeX seguindo as normas ABNT, está em `docs/documentacao.tex`.
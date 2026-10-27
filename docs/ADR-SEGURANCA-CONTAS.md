# ADR — Segurança das contas, e-mails dos chamados e reabertura

| | |
|---|---|
| **Status** | Aceito (decisões do desenvolvedor em 26/10/2026; implementado em 27/10/2026) |
| **Jira** | SCRUM-198 (história) e subtarefas SCRUM-199 a 206; SCRUM-183, SCRUM-184, SCRUM-207, SCRUM-208 |
| **Plano de origem** | `PLANO-SEGURANCA-CONTAS-E-EMAILS.md` (Projeto no claude.ai) |
| **Substitui** | a matriz de destinatários do `ARQUITETURA-EMAILS.md` §2 |

## 1. Contexto

O CentralDesk guarda conversas internas e o histórico de atendimento da empresa. Os e-mails de login
seguem o padrão `nome.sobrenome@contabilizape.com.br`, fáceis de adivinhar, e até aqui uma senha vazada
bastava para entrar. Foi pedido "pesar a mão" nas políticas: bloqueio por tentativas, códigos por e-mail,
verificação em duas etapas, confirmação do e-mail no cadastro e recuperação de senha, sem brechas.

## 2. Códigos por e-mail (SCRUM-201)

**Decisão:** JDK puro. `SecureRandom` gera 6 dígitos (`"%06d".formatted(random.nextInt(1_000_000))`);
o banco guarda só `HMAC-SHA256(OTP_HMAC_KEY, finalidade|usuário|código)`; a conferência usa
`MessageDigest.isEqual` (tempo constante).

**Por quê:** 1 milhão de combinações cairia em milissegundos com um hash simples se o banco vazasse;
com a chave fora do banco, não. Amarrar finalidade e usuário impede que o código de "esqueci minha senha"
sirva de código de acesso, ou o de uma pessoa no fluxo de outra. Bibliotecas TOTP (aplicativo
autenticador) e o One-Time Token Login do Spring Security foram descartados: não se encaixam no fluxo
próprio (JWT em cookie) e não acrescentam nada para código por e-mail.

**Política única:** validade de 10 min (acesso: 5), 5 tentativas por código, uso único, novo pedido
invalida o anterior, 60 s entre pedidos, 5 por hora por conta e finalidade, 20 por hora por IP; faxina
diária (`AccountMaintenanceJob`) dos vencidos há mais de 7 dias. A conferência roda em transação própria
(`REQUIRES_NEW`), para a tentativa errada ficar gravada mesmo quando a operação é recusada.

## 3. Bloqueio após 5 tentativas (SCRUM-200, 202)

- O bloqueio é um **estado da conta** (`users.status = BLOCKED`, V18), não uma tabela à parte: todas as
  rotas e a renovação de sessão já conferem o status.
- Contam: senha errada e, com a senha certa, código de acesso errado (prova de que a senha vazou, B5).
  Não contam: código errado do "esqueci minha senha" (contar permitiria bloquear alguém sem saber nada).
- As 5 falhas são **seguidas desde o último acesso completo**, sem prazo para zerar (S3).
- Na 5ª: sessões e navegadores confiáveis revogados na hora, e-mail ao dono e a cada admin ativo,
  auditoria (`security_events`, V20).
- **Respostas:** senha errada → 401 genérico em qualquer estado (quem ataca não fica sabendo do
  bloqueio); senha certa em conta bloqueada → 403 `code: account-blocked`. Nenhum token sai em caso algum.
- **Corrida:** a linha do usuário é travada no login (`SELECT ... FOR UPDATE`); 7 tentativas simultâneas
  contam até 5 e bloqueiam uma vez só (teste de integração).
- **Limite por IP** (contra o bloqueio em massa, B1): 20 tentativas **erradas** em 15 min por IP, em
  memória → 429 com `Retry-After`. Só as falhas contam, porque na rede da empresa todos saem pelo mesmo IP.

**Divergência do plano:** o plano sugeria `server.forward-headers-strategy=framework`. Foi usado `native`:
o `ForwardedHeaderFilter` do Spring confia no primeiro valor do `X-Forwarded-For`, que o cliente controla;
o `RemoteIpValve` do Tomcat lê da direita para a esquerda e só confia em proxies internos. **Conferir após o
deploy** que `security_events.ip` mostra o IP real de quem acessa (e não o do proxy do Railway nem o do
nginx do front); se mostrar o do proxy, ajustar `server.tomcat.remoteip.internal-proxies`.

## 4. Redefinição pelo administrador e recuperação de emergência (SCRUM-203)

- **Único caminho de volta para uma conta bloqueada** (S1). `PUT /users/{id}/password-reset` gera uma
  senha temporária de 16 caracteres (`SecureRandom`, sem caracteres ambíguos), mostrada **só** na resposta
  ao admin: não vai por e-mail nem para o log. Vale 24 h; a conta volta a `ACTIVE`, falhas zeradas,
  sessões e navegadores revogados, dono avisado por e-mail (sem a senha).
- O admin nunca escolhe nem vê a senha definitiva (B10) e não redefine a própria por aqui.
- **Troca obrigatória:** enquanto `password_change_required`, o token leva o perfil
  `PASSWORD_CHANGE_REQUIRED` e o `PasswordChangeRequiredFilter` responde 403 `password-change-required` a
  tudo, menos `GET /users/me`, `PATCH /users/me/password` e `POST /auth/logout`. Senha temporária vencida →
  401 genérico.
- **Recuperação de emergência (S5):** `ADMIN_RECOVERY_EMAIL` + `ADMIN_PASSWORD` liberam, na subida, essa
  conta de admin se estiver **bloqueada ou desativada** (`AdminRecovery`), com `ADMIN_PASSWORD` como
  temporária. Se a variável for esquecida no servidor, as próximas subidas não fazem nada (a conta já está
  ativa) e só registram um aviso. Mesmo assim, remover as variáveis depois de usar.

## 5. Esqueci minha senha (SCRUM-204)

`POST /auth/password/forgot` responde **sempre** 202 com a mesma mensagem (B7): conta ativa recebe o código;
bloqueada recebe um aviso para procurar a Inovação (no máximo 3 por hora); desconhecida, nada (fica na
auditoria). `POST /auth/password/reset { email, code, newPassword }` grava a senha nova (regras do cadastro),
derruba sessões e navegadores e avisa o dono. Código errado → 400 `errors.code`, sem dizer se venceu, foi
usado ou nunca existiu. Não há link de troca no e-mail: o código é digitado na tela (evita phishing).

## 6. Confirmação do e-mail no cadastro (SCRUM-205)

- Conta nova nasce `PENDING_VERIFICATION`, **sem sessão**, e recebe o código. `POST /auth/email/verify`
  ativa, cria o pedido de entrada na Inovação (só agora, para não poluir Aprovações) e abre a sessão; como o
  código chegou na caixa de entrada, o navegador já fica confiável.
- **Cadastro sequestrado (B3):** novo cadastro com o e-mail ou o CPF de um **pendente** o substitui (quem
  tem a caixa de e-mail ganha); contas confirmadas nunca são substituídas (409). Pendente há mais de 24 h é
  apagado pela faxina diária (o e-mail e o CPF voltam a ficar livres).
- Login com a senha certa antes de confirmar → 403 `email-not-verified` e um novo código (respeitando os 60 s).
- Contas que já existiam foram marcadas como confirmadas na V18.

## 7. Verificação em duas etapas e navegadores confiáveis (SCRUM-206)

- Ligada por padrão; **obrigatória para administradores** (S4), mesmo que o banco diga o contrário.
- Login com a senha certa fora de um navegador confiável → **202** `{ tfaRequired: true }`, cookie
  `tfa_challenge` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/auth`, 5 min) e código por e-mail.
  **Nenhum token de sessão** existe nessa etapa. O desafio é guardado só como hash (`login_challenges`, V21),
  vale para a conta e a tentativa que acertaram a senha, e morre em 5 min, no uso ou após 5 códigos errados.
- `POST /auth/login/tfa { code, trustThisBrowser }` conclui. Código errado conta para o bloqueio e avisa o
  dono (uma vez por desafio). Sucesso avisa o dono de um "novo acesso".
- **"ID do navegador" (B8):** não existe identificador confiável de navegador e *fingerprinting* é instável e
  invasivo. O navegador confiável é um token aleatório no cookie `trusted_device` (`Path=/auth`, 30 dias
  **fixos**, S7); o banco guarda o SHA-256. Cada navegador tem o seu token e o seu prazo; no máximo 10 por
  conta (o mais antigo sai).
- **Revogação (B9):** troca de senha (qualquer caminho), troca do e-mail pelo admin, bloqueio, desativação,
  redefinição pelo admin, desligar a TFA e "remover todos". Implementada junto com o fim das sessões
  (`RefreshTokenService.endAllSessions` e `revokeAll`), para nenhum caminho esquecer.
- **Perfil:** desligar pede a senha atual **e** um código (S6); ligar não pede nada. Com a TFA ligada (e
  sempre para admins), trocar a senha pede código; na troca obrigatória da senha temporária, não.

## 8. E-mails dos chamados (SCRUM-183, 184) — matriz em vigor

Regras gerais: só contas `ACTIVE`; um e-mail por pessoa (quem ocupa dois papéis recebe o do mais
específico); quem fez a ação não recebe, salvo na reatribuição.

| Gatilho | Destinatários |
|---|---|
| Abrir | solicitante; cada admin ativo do setor de destino (o solicitante admin recebe só a versão dele) |
| Assumir | solicitante (nada, se o admin assumiu a própria demanda) |
| Reatribuir | solicitante, novo responsável e responsável anterior; quem reatribuiu só recebe se for o anterior ou o novo (prioridade: novo > anterior > solicitante) |
| Comentar | só o outro lado no momento do comentário; nada se não houver (fila, ou admin com a própria demanda) |
| Encerrar | solicitante e responsável, menos quem encerrou (E1) |
| Reabrir | quem volta a atender (o responsável, ou os admins do setor se voltou para a fila) e o solicitante se quem reabriu foi um admin; menos quem reabriu |

**Divergência do plano:** o §13.3 do plano dizia que "quem fez a ação não recebe" valeria também na
reatribuição; a regra pedida pelo desenvolvedor (quem reatribui recebe se for o responsável anterior ou o
novo) prevaleceu.

**E-mails de segurança:** 13 templates (códigos, senha alterada, redefinida pelo admin, conta bloqueada —
ao dono, aos admins e na tentativa de "esqueci" —, tentativa após a senha, novo acesso, TFA desligada).
O código **nunca** vai no assunto. Eles saem na **fila prioritária** (`priorityEmailExecutor`), separada dos
e-mails de chamado, depois do commit (`EmailDispatcher`). O assunto do aviso aos admins não leva o nome da
pessoa bloqueada (texto digitado por usuário não entra em assunto); o nome vai no corpo.

**Sem chave do Resend em produção (B6):** a aplicação não sobe sem `RESEND_API_KEY` quando
`EMAIL_REQUIRED=true` **ou no Railway** (detectado por `RAILWAY_ENVIRONMENT_NAME`): sem a chave, os
códigos iriam para o log. O `LoggingEmailSender` mascara o destinatário dos e-mails confidenciais e só
mostra o corpo em DEBUG.

## 9. Reabertura de chamados (SCRUM-208)

R1 reabrem o solicitante e os admins (admin pelo banco); R2 até 7 dias após o encerramento; R3 volta para o
mesmo responsável se ele ainda pode atender (ativo, da Inovação ou admin), senão para a fila (com
`queue.changed`, motivo `REOPENED`); R4 motivo de 10 a 1.000 caracteres, registrado como comentário;
R5 no máximo 3 vezes. O encerramento desfeito vai para `ticket_closures` (V22) e os campos `closed_*` do
chamado são limpos. A máquina de estados continua dizendo que `CLOSED` é final: só `Ticket.reopen` sai dele.

## 10. Admin assume a própria demanda (SCRUM-207)

Um administrador — e só ele — pode assumir, receber por reatribuição e encerrar o chamado que abriu
(demandas internas costumam ser específicas). A condição de admin é lida do banco, não do token: quem perde
o acesso de admin perde a permissão na hora. Para a Inovação que não é admin, a regra antiga vale.

## 11. Consequências

- **Testes:** `@SecurityIntegrationTest` roda com a política de produção (`StrictAccountSecurityPolicy`) num
  contexto próprio; o `@IntegrationTest` das demais funcionalidades usa uma política relaxada que só existe
  em `src/test` (sem confirmação de e-mail, sem TFA, sem limite por IP). **Não existe propriedade nem
  variável que desligue as regras em produção.**
- **Front-end:** login em duas etapas (202 + tela do código), telas de "esqueci minha senha", "confirme seu
  e-mail", "defina sua nova senha", seção Segurança do perfil, chip "Bloqueado" e "Redefinir senha" em
  Usuários, botão "Reabrir" no detalhe, "Assumir" nos próprios chamados para admin. Design no canvas antes.
- **Operação (antes do deploy):** `OTP_HMAC_KEY` no Railway; `RESEND_API_KEY` obrigatória (sem ela a
  aplicação não sobe lá); `FRONT_BASE_URL`; domínio do remetente verificado no Resend (SCRUM-185). Sem e-mail
  chegando, ninguém entra (TFA e confirmação dependem dele).
- **Trabalho futuro:** limpeza de `security_events` antigos e decisão de retenção (LGPD: hoje ficam com a
  conta, inclusive anonimizada); evento em tempo real para os admins quando uma conta é bloqueada;
  painel de auditoria; TOTP (aplicativo autenticador) como alternativa ao código por e-mail.

## 12. Migrations

| Migration | Conteúdo |
|---|---|
| V18 | status `PENDING_VERIFICATION`/`BLOCKED` e campos de segurança em `users` |
| V19 | `verification_codes` |
| V20 | `security_events` (o plano numerava como V21) |
| V21 | `trusted_devices` e `login_challenges` (o plano numerava `trusted_devices` como V20; `login_challenges` é nova) |
| V22 | `ticket_closures` |

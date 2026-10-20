package br.com.contabilizape.centraldesk.controller.doc;

/**
 * Exemplos de respostas de erro (ProblemDetail) usados na documentação dos endpoints.
 * Precisam refletir o que o GlobalExceptionHandler realmente devolve.
 */
public final class ProblemExamples {

    public static final String MEDIA_TYPE = "application/problem+json";

    public static final String INVALID_FIELDS = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/auth/register",
              "errors": {
                "cpf": "CPF inválido",
                "email": "deve ser um e-mail do domínio @contabilizape.com.br"
              }
            }""";

    public static final String DEPARTMENT_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Departamento não encontrado: 00000000-0000-0000-0000-000000000000",
              "instance": "/auth/register"
            }""";

    public static final String EMAIL_TAKEN = """
            {
              "type": "about:blank",
              "title": "Cadastro duplicado",
              "status": 409,
              "detail": "E-mail já cadastrado",
              "instance": "/auth/register"
            }""";

    public static final String CPF_TAKEN = """
            {
              "type": "about:blank",
              "title": "Cadastro duplicado",
              "status": 409,
              "detail": "CPF já cadastrado",
              "instance": "/auth/register"
            }""";

    public static final String LOGIN_BLANK_FIELDS = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/auth/login",
              "errors": {
                "email": "não deve estar em branco",
                "password": "não deve estar em branco"
              }
            }""";

    public static final String INVALID_CREDENTIALS = """
            {
              "type": "about:blank",
              "title": "Credenciais inválidas",
              "status": 401,
              "detail": "E-mail ou senha inválidos.",
              "instance": "/auth/login"
            }""";

    public static final String ACCOUNT_NOT_ACTIVE = """
            {
              "type": "about:blank",
              "title": "Conta inativa",
              "status": 403,
              "detail": "Esta conta não está ativa. Procure um administrador.",
              "instance": "/auth/login"
            }""";

    public static final String INVALID_SESSION = """
            {
              "type": "about:blank",
              "title": "Sessão inválida",
              "status": 401,
              "detail": "Sessão inválida. Entre novamente.",
              "instance": "/auth/refresh"
            }""";

    public static final String TICKET_INVALID_FIELDS = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/tickets",
              "errors": {
                "title": "não deve estar em branco",
                "categoryId": "não deve ser nulo"
              }
            }""";

    public static final String CATEGORY_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Categoria não encontrada: 00000000-0000-0000-0000-000000000000",
              "instance": "/tickets"
            }""";

    public static final String TICKET_EDIT_FORBIDDEN = """
            {
              "type": "about:blank",
              "title": "Acesso negado",
              "status": 403,
              "detail": "Só quem abriu o chamado pode editá-lo.",
              "instance": "/tickets/0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f"
            }""";

    public static final String TICKET_NOT_EDITABLE = """
            {
              "type": "about:blank",
              "title": "Chamado não editável",
              "status": 409,
              "detail": "O chamado já foi assumido e não pode mais ser editado.",
              "instance": "/tickets/0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f"
            }""";

    public static final String PROFILE_INVALID_FIELDS = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/users/me",
              "errors": {
                "fullName": "não deve estar em branco"
              }
            }""";

    public static final String CURRENT_PASSWORD_MISMATCH = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/users/me/password",
              "errors": {
                "currentPassword": "senha atual incorreta"
              }
            }""";

    public static final String WEAK_NEW_PASSWORD = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/users/me/password",
              "errors": {
                "newPassword": "deve conter ao menos uma letra e um número"
              }
            }""";

    public static final String NEW_PASSWORD_SAME_AS_CURRENT = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/users/me/password",
              "errors": {
                "newPassword": "deve ser diferente da senha atual"
              }
            }""";

    public static final String DELETE_ACCOUNT_WRONG_PASSWORD = """
            {
              "type": "about:blank",
              "title": "Dados inválidos",
              "status": 400,
              "detail": "Verifique os campos e tente novamente.",
              "instance": "/users/me",
              "errors": {
                "password": "senha incorreta"
              }
            }""";

    public static final String LAST_ADMIN = """
            {
              "type": "about:blank",
              "title": "Operação não permitida",
              "status": 409,
              "detail": "Esta é a última conta de administrador ativa e não pode ser removida.",
              "instance": "/users/me"
            }""";

    public static final String USER_NOT_FOUND = """
            {
              "type": "about:blank",
              "title": "Usuário não encontrado",
              "status": 404,
              "detail": "Usuário não encontrado.",
              "instance": "/users/0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f/anonymize"
            }""";

    public static final String ACCOUNT_NOT_DEACTIVATED = """
            {
              "type": "about:blank",
              "title": "Operação não permitida",
              "status": 409,
              "detail": "Só contas desativadas podem ser anonimizadas. Desative a conta antes.",
              "instance": "/users/0f8b2c1e-6d3a-4f5e-9b7c-1a2b3c4d5e6f/anonymize"
            }""";

    private ProblemExamples() {
    }
}

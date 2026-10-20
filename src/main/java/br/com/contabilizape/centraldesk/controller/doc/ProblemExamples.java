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

    private ProblemExamples() {
    }
}

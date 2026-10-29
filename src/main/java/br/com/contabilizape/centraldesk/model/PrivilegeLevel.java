package br.com.contabilizape.centraldesk.model;

/**
 * Níveis de privilégio de uma conta, exclusivos entre si (PLANO-REATRIBUICAO-SETOR-E-TODOS-CHAMADOS, PV1).
 * Atender chamados não é privilégio: vem do departamento (Inovação, TICKET_HANDLER), em qualquer nível (PV5).
 */
public enum PrivilegeLevel {
    ADMIN("Administrador"),
    COORDINATOR("Coordenador"),
    COLLABORATOR("Colaborador");

    private final String label;

    PrivilegeLevel(String label) {
        this.label = label;
    }

    /** Nome exibido ao usuário (e-mail de aviso). */
    public String label() {
        return label;
    }
}

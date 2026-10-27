package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.model.User;

/**
 * As exigências de segurança das contas num lugar só (PLANO-SEGURANCA-CONTAS-E-EMAILS.md).
 *
 * Em produção vale sempre {@link StrictAccountSecurityPolicy}: não existe propriedade nem variável de
 * ambiente que desligue estas regras. Os testes de integração das outras funcionalidades usam uma
 * versão própria (só no código de teste), para não precisar confirmar e-mail e código em cada login.
 */
public interface AccountSecurityPolicy {

    /** Conta nova só entra depois de confirmar o código enviado ao e-mail. */
    boolean emailVerificationRequired();

    /** O login desta conta pede o código por e-mail (salvo em navegador confiável). */
    boolean twoFactorRequired(User user, boolean admin);

    /** Tentativas erradas de login aceitas por IP, somando todas as contas, a cada 15 minutos. */
    int failedLoginsPerIpPer15Minutes();

    /** Pedidos de código por IP, somando todas as contas, por hora. */
    int codeRequestsPerIpPerHour();
}

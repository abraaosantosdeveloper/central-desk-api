package br.com.contabilizape.centraldesk.security;

import br.com.contabilizape.centraldesk.model.User;
import org.springframework.stereotype.Component;

/**
 * A política de produção. Para administradores a verificação em duas etapas é obrigatória (S4).
 */
@Component
public class StrictAccountSecurityPolicy implements AccountSecurityPolicy {

    @Override
    public boolean emailVerificationRequired() {
        return true;
    }

    @Override
    public boolean twoFactorRequired(User user, boolean admin) {
        return admin || user.isTwoFactorEnabled();
    }

    @Override
    public int failedLoginsPerIpPer15Minutes() {
        return 20;
    }

    @Override
    public int codeRequestsPerIpPerHour() {
        return 20;
    }
}

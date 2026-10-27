package br.com.contabilizape.centraldesk;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.security.AccountSecurityPolicy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Só para os testes das outras funcionalidades ({@link IntegrationTest}): contas entram sem confirmar o
 * e-mail, o login não pede código e não há limite por IP (todas as requisições do MockMvc vêm do mesmo
 * IP). As regras de segurança das contas são testadas com a política de produção, em
 * {@link SecurityIntegrationTest}. Esta classe não existe no código de produção.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RelaxedSecurityTestConfiguration {

    @Bean
    @Primary
    AccountSecurityPolicy relaxedAccountSecurityPolicy() {
        return new AccountSecurityPolicy() {
            @Override
            public boolean emailVerificationRequired() {
                return false;
            }

            @Override
            public boolean twoFactorRequired(User user, boolean admin) {
                return false;
            }

            @Override
            public int failedLoginsPerIpPer15Minutes() {
                return Integer.MAX_VALUE;
            }

            @Override
            public int codeRequestsPerIpPerHour() {
                return Integer.MAX_VALUE;
            }
        };
    }
}

package br.com.contabilizape.centraldesk.notification;

import br.com.contabilizape.centraldesk.model.User;
import br.com.contabilizape.centraldesk.model.UserStatus;
import br.com.contabilizape.centraldesk.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class DepartmentRequestNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(DepartmentRequestNotificationListener.class);

    private final UserRepository userRepository;

    public DepartmentRequestNotificationListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDepartmentRequestCreated(DepartmentRequestCreatedEvent event) {
        List<User> admins = userRepository.findAdminsByStatus(UserStatus.ACTIVE);

        // Provisório: troca por e-mail (Resend) e notificação na interface quando forem modeladas
        for (User admin : admins) {
            log.info("Notificar {}: {} pediu entrada em {} (pedido {})",
                    admin.getEmail(), event.requesterName(),
                    event.departmentName(), event.requestId());
        }
    }
}
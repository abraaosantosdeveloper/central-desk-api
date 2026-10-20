package br.com.contabilizape.centraldesk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Apaga as notificações de um usuário (anonimização). Provisório: a entidade Notification
 * chega com o painel de notificações (SCRUM-152), e então este método vai para o NotificationRepository.
 */
@Repository
public class NotificationCleanupRepository {

    private final JdbcTemplate jdbc;

    public NotificationCleanupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int deleteByRecipientId(UUID recipientId) {
        return jdbc.update("DELETE FROM notifications WHERE recipient_id = ?", recipientId);
    }
}

package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Conversa do chamado, da mais antiga para a mais nova; o autor vem junto (nome na resposta). */
    @EntityGraph(attributePaths = "author")
    List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}

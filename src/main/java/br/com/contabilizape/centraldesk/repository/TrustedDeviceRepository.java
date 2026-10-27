package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, UUID> {

    Optional<TrustedDevice> findByTokenHash(String tokenHash);

    /** Navegadores ainda confiáveis da conta, do mais novo para o mais antigo. */
    @Query("""
            select d from TrustedDevice d
            where d.user.id = :userId and d.revokedAt is null and d.expiresAt > :now
            order by d.createdAt desc
            """)
    List<TrustedDevice> findActive(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    /** Troca de senha, bloqueio, desativação, TFA desligada, "sair de todos": nenhum navegador continua confiável. */
    @Modifying
    @Query("update TrustedDevice d set d.revokedAt = :now where d.user.id = :userId and d.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}

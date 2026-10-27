package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.CodePurpose;
import br.com.contabilizape.centraldesk.model.VerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    /** O código vigente (o mais novo ainda não usado), travado: duas conferências ao mesmo tempo contam as duas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from VerificationCode c
            where c.user.id = :userId and c.purpose = :purpose and c.consumedAt is null
            order by c.createdAt desc limit 1
            """)
    Optional<VerificationCode> findActiveForUpdate(@Param("userId") UUID userId, @Param("purpose") CodePurpose purpose);

    List<VerificationCode> findByUserIdAndPurposeAndConsumedAtIsNull(UUID userId, CodePurpose purpose);

    @Query("select max(c.createdAt) from VerificationCode c where c.user.id = :userId and c.purpose = :purpose")
    Optional<OffsetDateTime> findLatestCreatedAt(@Param("userId") UUID userId, @Param("purpose") CodePurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, CodePurpose purpose, OffsetDateTime since);

    long countByRequestIpAndCreatedAtAfter(String requestIp, OffsetDateTime since);

    /** Limpeza diária: códigos vencidos há tempo (a contagem por hora só olha a última hora). */
    @Modifying
    @Query("delete from VerificationCode c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") OffsetDateTime before);
}

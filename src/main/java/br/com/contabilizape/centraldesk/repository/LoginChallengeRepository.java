package br.com.contabilizape.centraldesk.repository;

import br.com.contabilizape.centraldesk.model.LoginChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoginChallenge c join fetch c.user where c.tokenHash = :tokenHash")
    Optional<LoginChallenge> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<LoginChallenge> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from LoginChallenge c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") OffsetDateTime before);
}

package kz.birchat.api.repository;

import jakarta.persistence.LockModeType;
import kz.birchat.api.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshTokenEntity t
            SET t.revokedAt = :now
            WHERE t.userId = :userId
              AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("now") LocalDateTime now
    );
}

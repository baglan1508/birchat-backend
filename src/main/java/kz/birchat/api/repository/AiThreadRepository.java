package kz.birchat.api.repository;

import kz.birchat.api.entity.AiThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AiThreadRepository extends JpaRepository<AiThreadEntity, UUID> {

    @Query("""
        SELECT t FROM AiThreadEntity t
        JOIN FETCH t.company c
        JOIN FETCH t.user u
        WHERE c.id = :companyId
          AND u.id = :userId
          AND t.defaultThread = true
        """)
    Optional<AiThreadEntity> findDefaultThread(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId
    );
}
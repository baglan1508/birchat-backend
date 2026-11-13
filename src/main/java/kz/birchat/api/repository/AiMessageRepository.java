package kz.birchat.api.repository;

import kz.birchat.api.entity.AiMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, UUID> {

    @Query("""
        SELECT m FROM AiMessageEntity m
        WHERE m.thread.id = :threadId
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<AiMessageEntity> findLatestByThreadId(
            @Param("threadId") UUID threadId,
            Pageable pageable
    );

    /** Последние сообщения диалога, начиная с момента since (например, с начала сегодняшнего дня). */
    @Query("""
        SELECT m FROM AiMessageEntity m
        WHERE m.thread.id = :threadId
          AND m.createdAt >= :since
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<AiMessageEntity> findLatestByThreadIdSince(
            @Param("threadId") UUID threadId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM AiMessageEntity m WHERE m.thread.id = :threadId")
    int deleteByThreadId(@Param("threadId") UUID threadId);
}
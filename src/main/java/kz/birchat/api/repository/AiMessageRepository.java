package kz.birchat.api.repository;

import kz.birchat.api.entity.AiMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
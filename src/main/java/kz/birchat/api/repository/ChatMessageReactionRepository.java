package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMessageReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReactionEntity, UUID> {

    Optional<ChatMessageReactionEntity> findByMessageIdAndUserId(UUID messageId, UUID userId);

    @Query("""
            SELECT r FROM ChatMessageReactionEntity r
            WHERE r.message.id IN :messageIds
            ORDER BY r.createdAt ASC
            """)
    List<ChatMessageReactionEntity> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);
}

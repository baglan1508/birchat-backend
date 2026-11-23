package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMessageMentionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageMentionRepository extends JpaRepository<ChatMessageMentionEntity, UUID> {

    @Query("""
            SELECT mm FROM ChatMessageMentionEntity mm
            JOIN FETCH mm.user u
            WHERE mm.message.id IN :messageIds
            ORDER BY mm.createdAt ASC
            """)
    List<ChatMessageMentionEntity> findByMessageIds(@Param("messageIds") Collection<UUID> messageIds);

    @Modifying
    @Query("DELETE FROM ChatMessageMentionEntity mm WHERE mm.message.id = :messageId")
    int deleteByMessageId(@Param("messageId") UUID messageId);
}

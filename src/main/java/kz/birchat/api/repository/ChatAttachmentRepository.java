package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachmentEntity, UUID> {

    @Query("""
            SELECT a FROM ChatAttachmentEntity a
            JOIN FETCH a.file f
            WHERE a.message.id IN :messageIds
            ORDER BY a.createdAt ASC
            """)
    List<ChatAttachmentEntity> findByMessageIds(
            @Param("messageIds") Collection<UUID> messageIds
    );
}
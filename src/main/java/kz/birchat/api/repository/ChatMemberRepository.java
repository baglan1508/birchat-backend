package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMemberEntity, UUID> {

    boolean existsByChatIdAndUserId(UUID chatId, UUID userId);

    Optional<ChatMemberEntity> findByChatIdAndUserId(UUID chatId, UUID userId);

    long countByChatId(UUID chatId);

    @Query("""
            SELECT cm FROM ChatMemberEntity cm
            JOIN FETCH cm.user u
            WHERE cm.chat.id = :chatId
            ORDER BY cm.createdAt ASC
            """)
    List<ChatMemberEntity> findMembers(@Param("chatId") UUID chatId);

    @Query("""
            SELECT cm.user.id FROM ChatMemberEntity cm
            WHERE cm.chat.id = :chatId
              AND cm.user.id IN :userIds
            """)
    List<UUID> findExistingUserIds(
            @Param("chatId") UUID chatId,
            @Param("userIds") Collection<UUID> userIds
    );
}

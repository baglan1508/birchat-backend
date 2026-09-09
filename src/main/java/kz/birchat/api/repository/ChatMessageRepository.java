package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    @Query("""
            SELECT m
            FROM ChatMessageEntity m
            JOIN FETCH m.user u
            JOIN FETCH m.chat ch
            WHERE m.company.id = :companyId
              AND ch.type = 'GENERAL'
              AND m.isDeleted = false
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessageEntity> findGeneralChatMessages(@Param("companyId") UUID companyId);

    @Query("""
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
        """)
    Long countGeneralChatMessages(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId
    );

    @Query("""
            SELECT m
            FROM ChatMessageEntity m
            JOIN FETCH m.user u
            JOIN FETCH m.chat ch
            WHERE m.company.id = :companyId
              AND ch.type = 'GENERAL'
              AND m.isDeleted = false
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessageEntity> findLastGeneralChatMessage(
            @Param("companyId") UUID companyId,
            Pageable pageable
    );
    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
          AND (
                m.createdAt > :afterCreatedAt
                OR (m.createdAt = :afterCreatedAt AND m.id > :afterId)
          )
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<ChatMessageEntity> findGeneralChatMessagesAfter(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable
    );
    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<ChatMessageEntity> findLatestGeneralChatMessages(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            Pageable pageable
    );

    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
          AND (
                m.createdAt < :beforeCreatedAt
                OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId)
          )
        ORDER BY m.createdAt DESC, m.id DESC
        """)
    List<ChatMessageEntity> findGeneralChatMessagesBefore(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable
    );
    @Query("""
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
          AND m.user.id <> :userId
        """)
    Long countUnreadGeneralChatMessagesAll(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            @Param("userId") UUID userId
    );
    @Query("""
        SELECT COUNT(m) FROM ChatMessageEntity m
        WHERE m.company.id = :companyId
          AND m.chat.id = :chatId
          AND m.isDeleted = false
          AND m.user.id <> :userId
          AND (
                m.createdAt > :lastReadCreatedAt
                OR (m.createdAt = :lastReadCreatedAt AND m.id > :lastReadMessageId)
          )
        """)
    Long countUnreadGeneralChatMessagesAfter(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            @Param("userId") UUID userId,
            @Param("lastReadCreatedAt") LocalDateTime lastReadCreatedAt,
            @Param("lastReadMessageId") UUID lastReadMessageId
    );
}
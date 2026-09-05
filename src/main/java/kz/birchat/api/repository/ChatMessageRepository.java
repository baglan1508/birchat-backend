package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

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
            SELECT COUNT(m)
            FROM ChatMessageEntity m
            JOIN m.chat ch
            WHERE m.company.id = :companyId
              AND ch.type = 'GENERAL'
              AND m.isDeleted = false
            """)
    Long countGeneralChatMessages(@Param("companyId") UUID companyId);

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
          AND ch.type = 'GENERAL'
          AND m.isDeleted = false
        ORDER BY m.createdAt ASC
        """)
    List<ChatMessageEntity> findGeneralChatMessagesPaged(
            @Param("companyId") UUID companyId,
            Pageable pageable
    );

    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND ch.type = 'GENERAL'
          AND m.isDeleted = false
          AND m.createdAt > :afterCreatedAt
        ORDER BY m.createdAt ASC
        """)
    List<ChatMessageEntity> findGeneralChatMessagesAfter(
            @Param("companyId") UUID companyId,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            Pageable pageable
    );
}
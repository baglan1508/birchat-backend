package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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
    /**
     * Полнотекстовый поиск по сообщениям доступных пользователю чатов (индекс из V16):
     * общий чат, группы и личные чаты, где он участник; директор ищет по всем группам компании
     * (чужие личные чаты ему не видны).
     *
     * Запрос нормализуется той же функцией birchat.search_text, что и текст сообщений,
     * и разбирается с русской морфологией («договоры» находит «договора», «договором»).
     * Все слова должны встретиться в сообщении; последнее слово ищется как начало слова —
     * пользователь мог его не допечатать. Спецсимволы tsquery в запросе безопасны:
     * plainto_tsquery воспринимает запрос как обычный текст.
     *
     * chatId — только этот чат (строкой; пустая строка — все доступные чаты).
     * Удалённые группы не ищутся.
     *
     * Возвращает id (новые сверху), сами сообщения — {@link #findAllWithUserAndChatByIdIn}.
     */
    @Query(value = """
        SELECT CAST(m.id AS text)
        FROM birchat.chat_messages m
        JOIN birchat.chats ch ON ch.id = m.chat_id
        WHERE m.company_id = :companyId
          AND m.is_deleted = false
          AND m.content IS NOT NULL
          AND ch.deleted_at IS NULL
          AND (:chatId = '' OR m.chat_id = CAST(:chatId AS uuid))
          AND m.search_vector @@ CAST(
                regexp_replace(
                    CAST(plainto_tsquery('russian', birchat.search_text(:query)) AS text),
                    '''$',
                    ''':*'
                ) AS tsquery
          )
          AND (
                ch.type = 'GENERAL'
                OR (:director = true AND ch.type = 'GROUP')
                OR EXISTS (
                    SELECT 1 FROM birchat.chat_members cm
                    WHERE cm.chat_id = ch.id
                      AND cm.user_id = :userId
                )
          )
        ORDER BY m.created_at DESC, m.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<String> searchAccessibleMessageIds(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            @Param("query") String query,
            @Param("chatId") String chatId,
            @Param("limit") int limit
    );

    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.id IN :ids
        """)
    List<ChatMessageEntity> findAllWithUserAndChatByIdIn(@Param("ids") Collection<UUID> ids);
    /**
     * Контекст вопроса к AI: последние сообщения общего чата и групп, которые видит пользователь
     * (директор — все группы). Личные чаты и удалённые группы в AI не попадают.
     */
    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND ch.deletedAt IS NULL
          AND (
                ch.type = 'GENERAL'
                OR (ch.type = 'GROUP' AND (
                    :director = true
                    OR EXISTS (
                        SELECT cm.id FROM ChatMemberEntity cm
                        WHERE cm.chat.id = ch.id AND cm.user.id = :userId
                    )
                ))
          )
          AND m.isDeleted = false
          AND m.content IS NOT NULL
        ORDER BY m.createdAt DESC, m.id DESC
    """)
    List<ChatMessageEntity> findLatestMessagesForAiContext(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            Pageable pageable
    );

    /** Сообщения общего чата и всех групп компании за период — для сводки директора. */
    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.company.id = :companyId
          AND ch.type IN ('GENERAL', 'GROUP')
          AND ch.deletedAt IS NULL
          AND m.isDeleted = false
          AND m.content IS NOT NULL
          AND m.createdAt >= :periodStart
          AND m.createdAt <= :periodEnd
        ORDER BY m.createdAt DESC, m.id DESC
    """)
    List<ChatMessageEntity> findCompanyMessagesForAiSummary(
            @Param("companyId") UUID companyId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            Pageable pageable
    );

    @Query("""
    SELECT COUNT(m) FROM ChatMessageEntity m
    JOIN m.chat ch
    WHERE m.company.id = :companyId
      AND ch.type IN ('GENERAL', 'GROUP')
      AND ch.deletedAt IS NULL
      AND m.isDeleted = false
      AND m.content IS NOT NULL
      AND m.createdAt > :after
""")
    Long countCompanyMessagesAfterForAiSummary(
            @Param("companyId") UUID companyId,
            @Param("after") LocalDateTime after
    );

    /** Id сообщений чата, удалённых у всех после момента since. */
    @Query("""
        SELECT m.id FROM ChatMessageEntity m
        WHERE m.chat.id = :chatId
          AND m.deletedAt > :since
        ORDER BY m.deletedAt ASC
        """)
    List<UUID> findDeletedIdsSince(
            @Param("chatId") UUID chatId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );

    /** Сообщения чата, изменённые после since (кроме удалённых), по времени изменения. */
    @Query("""
        SELECT m FROM ChatMessageEntity m
        JOIN FETCH m.user u
        JOIN FETCH m.chat ch
        WHERE m.chat.id = :chatId
          AND m.editedAt > :since
          AND m.isDeleted = false
        ORDER BY m.editedAt ASC
        """)
    List<ChatMessageEntity> findEditedSince(
            @Param("chatId") UUID chatId,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
}

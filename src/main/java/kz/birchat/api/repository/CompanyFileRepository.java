package kz.birchat.api.repository;

import kz.birchat.api.entity.CompanyFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyFileRepository extends JpaRepository<CompanyFileEntity, UUID> {

    /**
     * Файлы, доступные пользователю: файлы компании (chat_id IS NULL) и файлы групп и личных чатов,
     * где он участник. Директор видит файлы всех групп, но не чужих личных чатов. Файлы удалённых групп
     * и файлы, у которых все сообщения удалены у всех, не показываются.
     *
     * scope: ALL — все доступные; COMPANY — файлы компании (общий чат); CHAT — файлы чата chatId.
     * kind: ALL, MEDIA (фото и видео), VOICE (аудио), DOCUMENTS (остальное).
     * beforeSet — постранично: файлы старше (beforeCreatedAt, beforeId).
     */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        JOIN FETCH f.uploadedBy u
        WHERE f.company.id = :companyId
          AND f.status = 'READY'
          AND (
                f.chat IS NULL
                OR (:director = true AND EXISTS (
                    SELECT c.id FROM ChatEntity c
                    WHERE c.id = f.chat.id AND c.type = 'GROUP' AND c.deletedAt IS NULL
                ))
                OR EXISTS (
                    SELECT cm.id FROM ChatMemberEntity cm
                    WHERE cm.chat.id = f.chat.id
                      AND cm.user.id = :userId
                      AND cm.chat.deletedAt IS NULL
                )
          )
          AND (
                NOT EXISTS (SELECT a.id FROM ChatAttachmentEntity a WHERE a.file.id = f.id)
                OR EXISTS (
                    SELECT a.id FROM ChatAttachmentEntity a
                    WHERE a.file.id = f.id AND a.message.isDeleted = false
                )
          )
          AND (
                :scope = 'ALL'
                OR (:scope = 'COMPANY' AND f.chat IS NULL)
                OR (:scope = 'CHAT' AND f.chat.id = :chatId)
          )
          AND (
                :kind = 'ALL'
                OR (:kind = 'MEDIA' AND (LOWER(COALESCE(f.contentType, '')) LIKE 'image/%'
                                         OR LOWER(COALESCE(f.contentType, '')) LIKE 'video/%'))
                OR (:kind = 'VOICE' AND LOWER(COALESCE(f.contentType, '')) LIKE 'audio/%')
                OR (:kind = 'DOCUMENTS' AND NOT (LOWER(COALESCE(f.contentType, '')) LIKE 'image/%'
                                                 OR LOWER(COALESCE(f.contentType, '')) LIKE 'video/%'
                                                 OR LOWER(COALESCE(f.contentType, '')) LIKE 'audio/%'))
          )
          AND (
                :beforeSet = false
                OR f.createdAt < :beforeCreatedAt
                OR (f.createdAt = :beforeCreatedAt AND f.id < :beforeId)
          )
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<CompanyFileEntity> findAccessibleFiles(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            @Param("scope") String scope,
            @Param("chatId") UUID chatId,
            @Param("kind") String kind,
            @Param("beforeSet") boolean beforeSet,
            @Param("beforeCreatedAt") java.time.LocalDateTime beforeCreatedAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable
    );

    /**
     * Поиск по имени файла среди доступных файлов (правила — как у {@link #findAccessibleFiles}).
     * Подстрока ищется буквально (% и _ не спецсимволы).
     */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        JOIN FETCH f.uploadedBy u
        WHERE f.company.id = :companyId
          AND f.status = 'READY'
          AND LOCATE(LOWER(:query), LOWER(f.originalFileName)) > 0
          AND (
                f.chat IS NULL
                OR (:director = true AND EXISTS (
                    SELECT c.id FROM ChatEntity c
                    WHERE c.id = f.chat.id AND c.type = 'GROUP' AND c.deletedAt IS NULL
                ))
                OR EXISTS (
                    SELECT cm.id FROM ChatMemberEntity cm
                    WHERE cm.chat.id = f.chat.id
                      AND cm.user.id = :userId
                      AND cm.chat.deletedAt IS NULL
                )
          )
          AND (
                NOT EXISTS (SELECT a.id FROM ChatAttachmentEntity a WHERE a.file.id = f.id)
                OR EXISTS (
                    SELECT a.id FROM ChatAttachmentEntity a
                    WHERE a.file.id = f.id AND a.message.isDeleted = false
                )
          )
          AND (
                :scope = 'ALL'
                OR (:scope = 'COMPANY' AND f.chat IS NULL)
                OR (:scope = 'CHAT' AND f.chat.id = :chatId)
          )
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<CompanyFileEntity> searchAccessibleFiles(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            @Param("query") String query,
            @Param("scope") String scope,
            @Param("chatId") UUID chatId,
            Pageable pageable
    );

    /** Недозагруженные файлы старше cutoff — для ночной чистки. */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        WHERE f.status = 'PENDING'
          AND f.createdAt < :cutoff
        ORDER BY f.createdAt ASC
        """)
    List<CompanyFileEntity> findStalePending(
            @Param("cutoff") java.time.LocalDateTime cutoff,
            Pageable pageable
    );

    /**
     * Компания не дала согласия на AI — её файлы в очереди не ждут, а пропускаются:
     * иначе после включения AI модели ушёл бы весь накопленный архив.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE CompanyFileEntity f
        SET f.aiStatus = 'SKIPPED', f.aiError = 'AI выключен в компании'
        WHERE f.aiStatus = 'PENDING'
          AND f.company.id IN (SELECT c.id FROM CompanyEntity c WHERE c.aiEnabled = false)
        """)
    int skipPendingOfCompaniesWithoutAi();

    /** Очередь обработки для AI: ожидающие файлы компаний с включённым AI, у которых подошло время попытки. */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        WHERE f.aiStatus = 'PENDING'
          AND f.status = 'READY'
          AND f.company.aiEnabled = true
          AND (f.aiNextAttemptAt IS NULL OR f.aiNextAttemptAt <= :now)
        ORDER BY f.createdAt ASC
        """)
    List<CompanyFileEntity> findPendingForAi(
            @Param("now") java.time.LocalDateTime now,
            Pageable pageable
    );

    /** Сколько файлов компании получили описание для AI после указанного момента. */
    @Query("""
        SELECT COUNT(f) FROM CompanyFileEntity f
        WHERE f.company.id = :companyId
          AND f.aiStatus = 'DONE'
          AND f.aiProcessedAt > :after
        """)
    long countAnalyzedAfter(
            @Param("companyId") UUID companyId,
            @Param("after") java.time.LocalDateTime after
    );

    /**
     * Голосовые, которые прошли мимо расшифровки во время деплоя: старая версия приложения
     * не ставила аудио в очередь (ai_status = NULL) или помечала его «формат не поддерживается».
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        UPDATE CompanyFileEntity f
        SET f.aiStatus = 'PENDING', f.aiAttempts = 0, f.aiError = NULL, f.aiNextAttemptAt = NULL
        WHERE f.status = 'READY'
          AND f.contentType LIKE 'audio/%'
          AND f.createdAt > :since
          AND (f.aiStatus IS NULL
               OR (f.aiStatus = 'SKIPPED' AND f.aiError LIKE 'Формат не поддерживается%'))
        """)
    int requeueMissedVoices(@Param("since") java.time.LocalDateTime since);
}

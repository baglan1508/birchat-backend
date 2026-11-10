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
     * Файлы, доступные пользователю: файлы компании (chat_id IS NULL) и файлы групп,
     * где он участник. Директор видит все файлы компании.
     */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        JOIN FETCH f.uploadedBy u
        WHERE f.company.id = :companyId
          AND f.status = 'READY'
          AND (
                f.chat IS NULL
                OR :director = true
                OR EXISTS (
                    SELECT cm.id FROM ChatMemberEntity cm
                    WHERE cm.chat.id = f.chat.id
                      AND cm.user.id = :userId
                )
          )
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<CompanyFileEntity> findAccessibleFiles(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            Pageable pageable
    );

    /**
     * Поиск по имени файла среди доступных файлов. Подстрока ищется буквально (% и _ не спецсимволы).
     */
    @Query("""
        SELECT f FROM CompanyFileEntity f
        JOIN FETCH f.uploadedBy u
        WHERE f.company.id = :companyId
          AND f.status = 'READY'
          AND LOCATE(LOWER(:query), LOWER(f.originalFileName)) > 0
          AND (
                f.chat IS NULL
                OR :director = true
                OR EXISTS (
                    SELECT cm.id FROM ChatMemberEntity cm
                    WHERE cm.chat.id = f.chat.id
                      AND cm.user.id = :userId
                )
          )
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<CompanyFileEntity> searchAccessibleFiles(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director,
            @Param("query") String query,
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
}

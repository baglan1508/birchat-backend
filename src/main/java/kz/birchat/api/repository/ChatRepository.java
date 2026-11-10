package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    /**
     * Только для GENERAL: у компании ровно один общий чат.
     */
    Optional<ChatEntity> findByCompanyIdAndType(UUID companyId, String type);

    /**
     * Чаты, которые видит пользователь: общий чат и группы, где он участник.
     * Директор видит все группы компании.
     */
    @Query("""
            SELECT c FROM ChatEntity c
            WHERE c.company.id = :companyId
              AND (
                    c.type = 'GENERAL'
                    OR :director = true
                    OR EXISTS (
                        SELECT cm.id FROM ChatMemberEntity cm
                        WHERE cm.chat.id = c.id
                          AND cm.user.id = :userId
                    )
              )
            ORDER BY CASE WHEN c.type = 'GENERAL' THEN 0 ELSE 1 END, c.createdAt ASC
            """)
    List<ChatEntity> findAccessibleChats(
            @Param("companyId") UUID companyId,
            @Param("userId") UUID userId,
            @Param("director") boolean director
    );
}

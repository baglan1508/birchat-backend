package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatReadStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatReadStateRepository extends JpaRepository<ChatReadStateEntity, UUID> {

    @Query("""
            SELECT s FROM ChatReadStateEntity s
            WHERE s.company.id = :companyId
              AND s.chat.id = :chatId
              AND s.user.id = :userId
            """)
    Optional<ChatReadStateEntity> findState(
            @Param("companyId") UUID companyId,
            @Param("chatId") UUID chatId,
            @Param("userId") UUID userId
    );
}
package kz.birchat.api.repository;

import kz.birchat.api.entity.ChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    Optional<ChatEntity> findByCompanyIdAndType(UUID companyId, String type);
}
package kz.birchat.api.repository;

import kz.birchat.api.entity.AiCompanySummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AiCompanySummaryRepository extends JpaRepository<AiCompanySummaryEntity, UUID> {

    Optional<AiCompanySummaryEntity> findFirstByCompanyIdAndGeneratedAtAfterOrderByGeneratedAtDesc(
            UUID companyId,
            LocalDateTime generatedAfter
    );

    Optional<AiCompanySummaryEntity> findFirstByCompanyIdOrderByGeneratedAtDesc(UUID companyId);
}
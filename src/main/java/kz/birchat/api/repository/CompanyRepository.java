package kz.birchat.api.repository;

import kz.birchat.api.entity.CompanyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<CompanyEntity, UUID> {
    List<CompanyEntity> findByStatus(String status);

    List<CompanyEntity> findByStatusAndAiEnabledTrue(String status);

    /** Включён ли AI в компании; false — если компании нет. */
    @Query("SELECT COUNT(c) > 0 FROM CompanyEntity c WHERE c.id = :companyId AND c.aiEnabled = true")
    boolean isAiEnabled(@Param("companyId") UUID companyId);
}

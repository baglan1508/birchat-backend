package kz.birchat.api.repository;

import kz.birchat.api.entity.CompanyFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyFileRepository extends JpaRepository<CompanyFileEntity, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<CompanyFileEntity> findByCompanyIdOrderByCreatedAtDesc(
            UUID companyId,
            Pageable pageable
    );
}
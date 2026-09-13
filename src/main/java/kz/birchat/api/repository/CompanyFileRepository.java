package kz.birchat.api.repository;

import kz.birchat.api.entity.CompanyFileEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompanyFileRepository extends JpaRepository<CompanyFileEntity, UUID> {

    @EntityGraph(attributePaths = {"uploadedBy"})
    List<CompanyFileEntity> findByCompanyIdOrderByCreatedAtDesc(
            UUID companyId,
            Pageable pageable
    );

    @Query("""
        SELECT f FROM CompanyFileEntity f
        JOIN FETCH f.uploadedBy u
        WHERE f.company.id = :companyId
          AND LOWER(f.originalFileName) LIKE LOWER(CONCAT('%', :query, '%'))
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<CompanyFileEntity> searchFiles(
            @Param("companyId") UUID companyId,
            @Param("query") String query,
            Pageable pageable
    );
}
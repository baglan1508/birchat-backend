package kz.birchat.api.repository;

import kz.birchat.api.entity.CompanyMemberEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMemberRepository extends JpaRepository<CompanyMemberEntity, UUID> {

    @EntityGraph(attributePaths = {"company", "role"})
    List<CompanyMemberEntity> findByUserIdAndStatus(UUID userId, String status);

    @EntityGraph(attributePaths = {"company", "role"})
    Optional<CompanyMemberEntity> findByCompanyIdAndUserIdAndStatus(
            UUID companyId,
            UUID userId,
            String status
    );

    @EntityGraph(attributePaths = {"user", "role"})
    List<CompanyMemberEntity> findByCompanyIdAndStatusOrderByJoinedAtAsc(
            UUID companyId,
            String status
    );

    boolean existsByCompanyIdAndUserIdAndStatus(
            UUID companyId,
            UUID userId,
            String status
    );

    long countByCompanyIdAndStatus(UUID companyId, String status);

    /** Все активные участия в компании → новый статус (удаление компании). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE CompanyMemberEntity m SET m.status = :newStatus
        WHERE m.company.id = :companyId AND m.status = 'ACTIVE'
        """)
    int changeActiveStatus(
            @org.springframework.data.repository.query.Param("companyId") UUID companyId,
            @org.springframework.data.repository.query.Param("newStatus") String newStatus
    );
}

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
}
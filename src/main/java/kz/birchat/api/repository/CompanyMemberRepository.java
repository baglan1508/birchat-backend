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

    /** Участие в компании в любом статусе (пара компания + человек уникальна). */
    @EntityGraph(attributePaths = {"user", "role"})
    Optional<CompanyMemberEntity> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    /** Человек открыл список своих компаний — во всех новых для него компаниях он «зашёл». */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE CompanyMemberEntity m SET m.acceptedAt = :now
        WHERE m.user.id = :userId AND m.status = 'ACTIVE' AND m.acceptedAt IS NULL
        """)
    int markAccepted(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now
    );

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

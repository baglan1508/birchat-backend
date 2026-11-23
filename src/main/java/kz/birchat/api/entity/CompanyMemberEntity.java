package kz.birchat.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "company_members", schema = "birchat")
public class CompanyMemberEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "position")
    private String position;

    @Column(name = "status")
    private String status;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    /** Имя, под которым директор добавил сотрудника. Показывается коллегам, пока он не зашёл в компанию. */
    @Column(name = "invited_name")
    private String invitedName;

    /** Когда сотрудник впервые получил компанию в своём списке (GET /companies/my). null — ещё не заходил. */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    public boolean isPending() {
        return acceptedAt == null;
    }
}
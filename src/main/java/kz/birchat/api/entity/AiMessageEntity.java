package kz.birchat.api.entity;

import jakarta.persistence.*;
import kz.birchat.api.enums.AiMessageRole;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_messages", schema = "birchat")
public class AiMessageEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private AiThreadEntity thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AiMessageRole role;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "model")
    private String model;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
package kz.birchat.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name="refresh_tokens", schema="birchat")
public class RefreshTokenEntity {
    @Id private UUID id;
    @Column(name="user_id", nullable=false) private UUID userId;
    @Column(name="token_hash", nullable=false, length=64, unique=true) private String tokenHash;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(name="revoked_at") private LocalDateTime revokedAt;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
}

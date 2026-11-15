package kz.birchat.api.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "device_tokens", schema = "birchat")
public class DeviceTokenEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "environment", nullable = false)
    private String environment;

    @Column(name = "locale", nullable = false)
    private String locale;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

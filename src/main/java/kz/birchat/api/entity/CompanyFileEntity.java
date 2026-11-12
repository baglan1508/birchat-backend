package kz.birchat.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "company_files", schema = "birchat")
public class CompanyFileEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private UserEntity uploadedBy;

    /**
     * Групповой чат, которому принадлежит файл. NULL — файл виден всем сотрудникам компании.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private ChatEntity chat;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    /** PENDING — ссылка на загрузку выдана, файл ещё не подтверждён; READY — файл доступен. */
    @Column(name = "status", nullable = false)
    private String status;

    /** Обработка файла для AI (V17): NULL, PENDING, DONE, FAILED, SKIPPED. */
    @Column(name = "ai_status")
    private String aiStatus;

    /** Что на фото / расшифровка — для контекста AI. */
    @Column(name = "ai_text")
    private String aiText;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "ai_attempts", nullable = false)
    private int aiAttempts;

    @Column(name = "ai_error")
    private String aiError;

    @Column(name = "ai_next_attempt_at")
    private LocalDateTime aiNextAttemptAt;

    @Column(name = "ai_processed_at")
    private LocalDateTime aiProcessedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
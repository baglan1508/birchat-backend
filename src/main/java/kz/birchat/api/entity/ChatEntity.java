package kz.birchat.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chats", schema = "birchat")
public class ChatEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** У личного чата названия нет (null). */
    @Column(name = "name")
    private String name;

    @Column(name = "type", nullable = false)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Только у личного чата: «меньший userId:больший userId» — один чат на пару людей в компании. */
    @Column(name = "direct_key")
    private String directKey;

    /** Закреплённое сообщение (одно на чат). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_message_id")
    private ChatMessageEntity pinnedMessage;

    /** Группу удалили: чат скрыт у всех, участники и директор получают 403. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;
}
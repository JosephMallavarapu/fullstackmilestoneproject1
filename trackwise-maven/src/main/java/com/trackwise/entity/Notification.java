package com.trackwise.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "passwordHash" })
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;
    @Column(nullable = false)
    private String title;
    @Lob
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;
    @Column(name = "is_sent")
    @Builder.Default
    private Boolean isSent = false;
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    @Column(name = "related_id")
    private Long relatedId;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum NotificationType {
        EXPENSE_SUBMITTED, EXPENSE_APPROVED, EXPENSE_REJECTED,
        APPROVAL_REQUIRED, POLICY_VIOLATION, BUDGET_ALERT,
        ERP_SYNC_DONE, ERP_SYNC_ERROR, OCR_COMPLETE
    }

    public enum NotificationChannel {
        EMAIL, SMS, PUSH, IN_APP
    }
}

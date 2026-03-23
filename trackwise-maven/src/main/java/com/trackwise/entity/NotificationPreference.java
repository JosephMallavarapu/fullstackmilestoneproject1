package com.trackwise.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "passwordHash" })
    private User user;
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    @Column
    @Builder.Default
    private Boolean email = true;
    @Column
    @Builder.Default
    private Boolean sms = false;
    @Column
    @Builder.Default
    private Boolean push = false;
    @Column(name = "in_app")
    @Builder.Default
    private Boolean inApp = true;
}

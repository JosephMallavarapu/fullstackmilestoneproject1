package com.trackwise.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;
    @Column(nullable = false, length = 80)
    private String action;
    @Type(value = JsonType.class)
    @Column(name = "old_value", columnDefinition = "json")
    private Map<String, Object> oldValue;
    @Type(value = JsonType.class)
    @Column(name = "new_value", columnDefinition = "json")
    private Map<String, Object> newValue;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}

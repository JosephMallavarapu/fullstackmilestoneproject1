package com.trackwise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "erp_integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ErpProvider provider;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Column(name = "client_id", length = 255)
    private String clientId;
    @Column(name = "client_secret", length = 255)
    private String clientSecret;
    @Lob
    @Column(name = "access_token")
    private String accessToken;
    @Lob
    @Column(name = "refresh_token")
    private String refreshToken;
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;
    @Column(name = "company_id", length = 100)
    private String companyId;
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
    @Column(name = "total_synced")
    @Builder.Default
    private Integer totalSynced = 0;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ErpProvider {
        QUICKBOOKS, SAP_CONCUR, XERO, NETSUITE
    }
}

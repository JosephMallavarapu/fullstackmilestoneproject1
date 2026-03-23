package com.trackwise.entity;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "ocr_scans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OcrScan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "file_url", length = 500)
    private String fileUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OcrProvider provider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OcrStatus status;
    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;
    @Type(value = JsonType.class)
    @Column(name = "extracted_fields", columnDefinition = "json")
    private Map<String, String> extractedFields;
    @Lob
    @Column(name = "raw_text")
    private String rawText;
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum OcrProvider {
        TESSERACT, AWS_TEXTRACT, GOOGLE_VISION
    }

    public enum OcrStatus {
        PENDING, PROCESSING, SUCCESS, FAILED
    }
}

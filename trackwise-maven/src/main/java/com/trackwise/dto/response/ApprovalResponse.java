package com.trackwise.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {
    private Long id;
    private String reviewerName;
    private String action;
    private String comment;
    private LocalDateTime actedAt;
}

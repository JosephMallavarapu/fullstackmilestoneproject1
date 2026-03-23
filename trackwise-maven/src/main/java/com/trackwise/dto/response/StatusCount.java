package com.trackwise.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCount {
    private String status;
    private Long count;
}

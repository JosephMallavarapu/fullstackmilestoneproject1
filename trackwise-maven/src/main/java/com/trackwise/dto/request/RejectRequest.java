package com.trackwise.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectRequest {
    @NotNull
    private Long approverId;
    @NotBlank
    private String reason;
}

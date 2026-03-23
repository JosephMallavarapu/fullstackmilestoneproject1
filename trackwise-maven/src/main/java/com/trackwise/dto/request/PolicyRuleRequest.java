package com.trackwise.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRuleRequest {
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String ruleType;
    @NotBlank
    private String severity;
    @NotBlank
    private String action;
    private BigDecimal threshold;
}

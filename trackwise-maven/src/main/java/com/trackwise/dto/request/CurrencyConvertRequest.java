package com.trackwise.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyConvertRequest {
    @NotNull
    @Positive
    private BigDecimal amount;
    @NotBlank
    @Size(min = 3, max = 3)
    private String from;
    @NotBlank
    @Size(min = 3, max = 3)
    private String to;
}

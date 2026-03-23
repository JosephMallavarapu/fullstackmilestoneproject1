package com.trackwise.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;
    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;
    @Column(nullable = false, precision = 16, scale = 8)
    private BigDecimal rate;
    @Column(length = 50)
    private String source;
    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;
}

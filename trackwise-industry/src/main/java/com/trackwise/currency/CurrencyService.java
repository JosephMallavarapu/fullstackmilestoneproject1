package com.trackwise.currency;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ══════════════════════════════════════════════════════════════
 *  TrackWise — Multi-Currency & Forex Service
 *  Industry Feature: Live FX rates + expense currency conversion
 *
 *  Integration: exchangeratesapi.io (or ECB, OpenExchangeRates)
 *  Fallback:    cached rates updated every 6 hours
 *  PCI DSS:     no payment card data handled — amount conversion only
 * ══════════════════════════════════════════════════════════════
 */

// ── Response DTO ──────────────────────────────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class ConversionResult {
    private String     fromCurrency;
    private String     toCurrency;
    private BigDecimal originalAmount;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private LocalDateTime rateTimestamp;
    private String     rateSource;
}

// ── Supported currencies ──────────────────────────────────────
enum SupportedCurrency {
    USD, EUR, GBP, INR, JPY, CAD, AUD, CHF, SGD, AED;

    public static boolean isSupported(String code) {
        try { valueOf(code); return true; } catch (IllegalArgumentException e) { return false; }
    }
}

// ─────────────────────────────────────────────────────────────
// CurrencyService — live rates + conversion logic
// ─────────────────────────────────────────────────────────────
@Service
@Slf4j
public class CurrencyService {

    @Value("${app.fx.api-key:demo}")
    private String fxApiKey;

    @Value("${app.fx.base-url:https://api.exchangeratesapi.io/v1}")
    private String fxBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Live rates cache — updated every 6 hours via @Scheduled.
     * Key = "USD_EUR", "USD_GBP", etc.
     * Fallback values are ECB rates at time of writing.
     */
    private final Map<String, BigDecimal> ratesCache = new ConcurrentHashMap<>(Map.of(
        "USD_EUR", new BigDecimal("0.9234"),
        "USD_GBP", new BigDecimal("0.7891"),
        "USD_INR", new BigDecimal("83.420"),
        "USD_JPY", new BigDecimal("149.870"),
        "USD_CAD", new BigDecimal("1.3612"),
        "USD_AUD", new BigDecimal("1.5234"),
        "USD_CHF", new BigDecimal("0.8912"),
        "USD_SGD", new BigDecimal("1.3401"),
        "USD_AED", new BigDecimal("3.6725")
    ));

    private LocalDateTime lastRefresh = LocalDateTime.now();

    // ── Core: convert any currency to USD ────────────────────
    /**
     * Converts an amount from any supported currency to USD.
     * All expenses are stored as USD equivalent in amount_usd column.
     *
     * PCI DSS note: we only handle amounts — no card/payment data.
     */
    public ConversionResult toUSD(BigDecimal amount, String fromCurrency) {
        if ("USD".equalsIgnoreCase(fromCurrency)) {
            return ConversionResult.builder()
                    .fromCurrency("USD").toCurrency("USD")
                    .originalAmount(amount).convertedAmount(amount)
                    .exchangeRate(BigDecimal.ONE)
                    .rateTimestamp(LocalDateTime.now())
                    .rateSource("N/A (same currency)")
                    .build();
        }

        BigDecimal rate = getRateToUSD(fromCurrency);
        BigDecimal converted = amount.divide(rate, 2, RoundingMode.HALF_UP);

        return ConversionResult.builder()
                .fromCurrency(fromCurrency.toUpperCase())
                .toCurrency("USD")
                .originalAmount(amount)
                .convertedAmount(converted)
                .exchangeRate(rate)
                .rateTimestamp(lastRefresh)
                .rateSource("ExchangeRatesAPI.io")
                .build();
    }

    // ── Convert between any two currencies ────────────────────
    public ConversionResult convert(BigDecimal amount, String from, String to) {
        if (from.equalsIgnoreCase(to)) {
            return ConversionResult.builder()
                    .fromCurrency(from).toCurrency(to)
                    .originalAmount(amount).convertedAmount(amount)
                    .exchangeRate(BigDecimal.ONE)
                    .rateTimestamp(lastRefresh).rateSource("Same currency")
                    .build();
        }

        // Convert: from → USD → to
        BigDecimal fromToUsd = getRateToUSD(from);
        BigDecimal usdToTo   = getRateFromUSD(to);
        BigDecimal crossRate = usdToTo.divide(fromToUsd, 6, RoundingMode.HALF_UP);
        BigDecimal converted = amount.multiply(crossRate).setScale(2, RoundingMode.HALF_UP);

        return ConversionResult.builder()
                .fromCurrency(from.toUpperCase()).toCurrency(to.toUpperCase())
                .originalAmount(amount).convertedAmount(converted)
                .exchangeRate(crossRate)
                .rateTimestamp(lastRefresh)
                .rateSource("ExchangeRatesAPI.io (cross-rate)")
                .build();
    }

    // ── Get live rate for a currency pair ────────────────────
    @Cacheable("fx-rates")
    public BigDecimal getRate(String from, String to) {
        return getRateFromUSD(to).divide(getRateToUSD(from), 6, RoundingMode.HALF_UP);
    }

    // ── Refresh rates every 6 hours ───────────────────────────
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000)
    public void refreshRates() {
        try {
            String url = fxBaseUrl + "/latest?access_key=" + fxApiKey + "&base=USD";
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("rates")) {
                @SuppressWarnings("unchecked")
                Map<String, Number> rates = (Map<String, Number>) response.get("rates");
                rates.forEach((currency, rate) -> {
                    String key = "USD_" + currency;
                    ratesCache.put(key, new BigDecimal(rate.toString()));
                });
                lastRefresh = LocalDateTime.now();
                log.info("FX rates refreshed from API: {} currencies updated", rates.size());
            }
        } catch (Exception e) {
            log.warn("FX rate refresh failed — using cached rates: {}", e.getMessage());
        }
    }

    // ── All live rates (for UI display) ──────────────────────
    public Map<String, BigDecimal> getAllRates() {
        return Map.copyOf(ratesCache);
    }

    public LocalDateTime getLastRefreshTime() { return lastRefresh; }

    // ── Internal helpers ──────────────────────────────────────
    private BigDecimal getRateToUSD(String currency) {
        String key = "USD_" + currency.toUpperCase();
        return ratesCache.getOrDefault(key, BigDecimal.ONE);
    }

    private BigDecimal getRateFromUSD(String currency) {
        String key = "USD_" + currency.toUpperCase();
        return ratesCache.getOrDefault(key, BigDecimal.ONE);
    }
}

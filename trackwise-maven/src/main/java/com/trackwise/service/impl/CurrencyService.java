package com.trackwise.service.impl;

import com.trackwise.entity.CurrencyRate;
import com.trackwise.repository.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.*;

// ═══════════════════════════════════════════════════════════
//  CurrencyService — FX rate cache + amount conversion
//  Rates fetched from ExchangeRatesAPI; refreshed every hour.
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CurrencyService {

    private final CurrencyRateRepository currencyRateRepo;
    private final RestTemplate restTemplate;

    @Value("${trackwise.currency.api-url}") private String apiUrl;
    @Value("${trackwise.currency.api-key}") private String apiKey;
    @Value("${trackwise.currency.base:USD}") private String baseCurrency;

    /** Convert amount from one currency to another using cached rates. */
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) return amount;
        BigDecimal fromToBase = getRateToBase(fromCurrency);
        BigDecimal baseToTarget = getRateFromBase(toCurrency);
        return amount.multiply(fromToBase).multiply(baseToTarget);
    }

    /** Get rate: 1 targetCurrency = ? USD */
    public BigDecimal getRateToBase(String currency) {
        if (currency.equalsIgnoreCase(baseCurrency)) return BigDecimal.ONE;
        return currencyRateRepo.findByBaseCurrencyAndTargetCurrency(currency, baseCurrency)
            .map(CurrencyRate::getRate)
            .orElseGet(() -> {
                // Inverse lookup
                return currencyRateRepo.findByBaseCurrencyAndTargetCurrency(baseCurrency, currency)
                    .map(r -> BigDecimal.ONE.divide(r.getRate(), MathContext.DECIMAL64))
                    .orElse(BigDecimal.ONE);
            });
    }

    /** Get rate: 1 USD = ? targetCurrency */
    public BigDecimal getRateFromBase(String targetCurrency) {
        if (targetCurrency.equalsIgnoreCase(baseCurrency)) return BigDecimal.ONE;
        return currencyRateRepo.findByBaseCurrencyAndTargetCurrency(baseCurrency, targetCurrency)
            .map(CurrencyRate::getRate)
            .orElse(BigDecimal.ONE);
    }

    /** Return all cached rates (base = USD) */
    public List<CurrencyRate> getAllRates() {
        return currencyRateRepo.findByBaseCurrency(baseCurrency);
    }

    /** Scheduled refresh — every hour (cron in application.properties) */
    @Scheduled(cron = "${trackwise.currency.refresh-cron}")
    @SuppressWarnings("unchecked")
    public void refreshRates() {
        try {
            String url = apiUrl + "?access_key=" + apiKey + "&base=" + baseCurrency;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                Map<String, Number> rates = (Map<String, Number>) response.get("rates");
                rates.forEach((currency, rate) -> {
                    CurrencyRate cr = currencyRateRepo
                        .findByBaseCurrencyAndTargetCurrency(baseCurrency, currency)
                        .orElse(CurrencyRate.builder()
                            .baseCurrency(baseCurrency)
                            .targetCurrency(currency)
                            .source("ExchangeRatesAPI")
                            .build());
                    cr.setRate(BigDecimal.valueOf(rate.doubleValue()));
                    cr.setFetchedAt(LocalDateTime.now());
                    currencyRateRepo.save(cr);
                });
                log.info("CurrencyService: {} FX rates refreshed from API", rates.size());
            }
        } catch (Exception ex) {
            log.warn("CurrencyService: FX refresh failed — using cached rates. {}", ex.getMessage());
        }
    }
}

package com.aftersales.copilot.statistics.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class AiPricing {
    public record Price(String provider, String model, BigDecimal inputCnyPerMillion,
                        BigDecimal outputCnyPerMillion, String currency, LocalDate effectiveFrom,
                        String sourceUrl, boolean verified) {}
    public record Estimate(Long micros, String status, String source) {}
    private final List<Price> prices;

    public AiPricing(ObjectMapper mapper, @Value("${app.ai.prices-json:[]}") String json) {
        try {
            prices = mapper.readValue(json, new TypeReference<List<Price>>() {});
            for (Price price : prices) {
                if (price.provider() == null || price.model() == null || price.effectiveFrom() == null
                        || price.inputCnyPerMillion() == null || price.outputCnyPerMillion() == null
                        || price.inputCnyPerMillion().signum() < 0 || price.outputCnyPerMillion().signum() < 0
                        || !"CNY".equals(price.currency()) || price.sourceUrl() == null
                        || !price.sourceUrl().startsWith("https://")) {
                    throw new IllegalArgumentException("Price requires CNY rates, effective date and HTTPS source");
                }
            }
            long distinct = prices.stream().map(p -> p.provider() + "\n" + p.model() + "\n" + p.effectiveFrom()).distinct().count();
            if (distinct != prices.size()) throw new IllegalArgumentException("Duplicate effective model price");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid AI_PRICES_JSON configuration", e);
        }
    }

    public Estimate estimate(String provider, String model, Long input, Long output, LocalDate date) {
        if ("fake".equals(provider) && ("fake-v1".equals(model) || "fake-embedding-v1".equals(model))) {
            return new Estimate(0L, "FREE_FAKE", null);
        }
        if (input == null || output == null) return new Estimate(null, "UNKNOWN_USAGE", null);
        Price price = prices.stream().filter(p -> p.verified() && p.provider().equals(provider)
                        && p.model().equals(model) && !p.effectiveFrom().isAfter(date))
                .max(java.util.Comparator.comparing(Price::effectiveFrom)).orElse(null);
        if (price == null) return new Estimate(null, "UNKNOWN_PRICE", null);
        // CNY / million tokens * token count equals CNY micros, with no floating-point conversion.
        long micros = price.inputCnyPerMillion().multiply(BigDecimal.valueOf(input))
                .add(price.outputCnyPerMillion().multiply(BigDecimal.valueOf(output)))
                .setScale(0, RoundingMode.CEILING).longValueExact();
        return new Estimate(micros, "ESTIMATED", price.sourceUrl());
    }
}

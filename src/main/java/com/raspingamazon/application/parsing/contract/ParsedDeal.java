package com.raspingamazon.application.parsing.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representa uma oferta identificada e normalizada pelo parser.
 *
 * <p>Este objeto não representa uma decisão de negócio.
 * Campos ausentes permanecem como null.</p>
 */
public record ParsedDeal(
        String asin,
        String productUrl,
        String title,
        String imageUrl,
        BigDecimal currentPrice,
        BigDecimal basisPrice,
        BigDecimal previousPrice,
        BigDecimal soldPercentage,
        OffsetDateTime collectedAt,
        String source
) {
}
package com.raspingamazon.application.enrichment.contract;

/**
 * Evidência de quantidade de avaliações observada durante o
 * enrichment da página individual do produto.
 *
 * <p>O texto bruto é preservado para auditoria e o valor normalizado
 * permanece opcional quando a fonte não puder ser interpretada com
 * segurança.</p>
 */
public record ReviewCountEvidence(
    String rawValue,
    Long reviewCount,
    String source
) {

    public ReviewCountEvidence {

        rawValue =
            normalizeNullableText(
                rawValue
            );

        source =
            normalizeNullableText(
                source
            );

        if ((rawValue == null)
            != (source == null)) {

            throw new IllegalArgumentException(
                "Review-count rawValue and source must be both present or both absent"
            );
        }

        if (reviewCount != null) {

            if (reviewCount < 0) {

                throw new IllegalArgumentException(
                    "Review count must not be negative"
                );
            }

            if (rawValue == null) {

                throw new IllegalArgumentException(
                    "Normalized review count requires raw evidence"
                );
            }
        }
    }

    /**
     * Representa ausência de evidência confiável de reviewCount.
     */
    public static ReviewCountEvidence unavailable() {

        return new ReviewCountEvidence(
            null,
            null,
            null
        );
    }

    /**
     * Informa se existe reviewCount normalizado utilizável pelo snapshot.
     */
    public boolean available() {

        return reviewCount != null;
    }

    private static String normalizeNullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        return normalized.isEmpty()
            ? null
            : normalized;
    }
}

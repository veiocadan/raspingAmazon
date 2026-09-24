package com.raspingamazon.application.enrichment.contract;

/**
 * Evidência de rating observada durante o enrichment da página
 * individual do produto.
 *
 * <p>O valor bruto preserva exatamente o atributo textual utilizado
 * pelo parser. O valor normalizado é opcional porque a fonte pode
 * existir e ainda assim não ser interpretável com segurança.</p>
 */
public record RatingEvidence(
    String rawValue,
    Double rating,
    String source
) {

    private static final double MIN_RATING = 0.0d;

    private static final double MAX_RATING = 5.0d;

    public RatingEvidence {

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
                "Rating rawValue and source must be both present or both absent"
            );
        }

        if (rating != null) {

            if (!Double.isFinite(
                rating
            )) {

                throw new IllegalArgumentException(
                    "Rating must be finite when present"
                );
            }

            if (rating < MIN_RATING
                || rating > MAX_RATING) {

                throw new IllegalArgumentException(
                    "Rating must be between 0 and 5"
                );
            }

            if (rawValue == null) {

                throw new IllegalArgumentException(
                    "Normalized rating requires raw evidence"
                );
            }
        }
    }

    /**
     * Representa ausência de evidência confiável de rating.
     */
    public static RatingEvidence unavailable() {

        return new RatingEvidence(
            null,
            null,
            null
        );
    }

    /**
     * Informa se existe rating normalizado utilizável pelo snapshot.
     */
    public boolean available() {

        return rating != null;
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

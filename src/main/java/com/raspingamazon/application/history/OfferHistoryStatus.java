package com.raspingamazon.application.history;

import com.raspingamazon.domain.product.Asin;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Read model agregado do estado histórico operacional de um ASIN.
 *
 * <p>Este objeto responde perguntas temporais e operacionais sem
 * modificar entidades do domínio:</p>
 *
 * <ul>
 *     <li>quantos snapshots existem;</li>
 *     <li>quando o ASIN foi detectado pela primeira vez;</li>
 *     <li>quando ocorreu a última atualização;</li>
 *     <li>se a oferta é recorrente;</li>
 *     <li>se alguma ocorrência já foi publicada com sucesso.</li>
 * </ul>
 *
 * <p>O conceito de "já publicada" considera exclusivamente
 * Publication com status PUBLISHED.</p>
 */
public record OfferHistoryStatus(
    Asin asin,
    long snapshotCount,
    OffsetDateTime firstDetectedAt,
    OffsetDateTime lastUpdatedAt,
    boolean publishedBefore
) {

    public OfferHistoryStatus {

        asin =
            Objects.requireNonNull(
                asin,
                "OfferHistoryStatus asin must not be null"
            );

        if (snapshotCount < 0) {

            throw new IllegalArgumentException(
                "OfferHistoryStatus snapshotCount must not be negative"
            );
        }

        /*
         * Um produto conhecido pode, estruturalmente, ainda não possuir
         * snapshots.
         *
         * Nesse caso os dois timestamps precisam permanecer ausentes
         * e não pode existir publicação anterior.
         */
        if (snapshotCount == 0) {

            if (firstDetectedAt != null
                || lastUpdatedAt != null) {

                throw new IllegalArgumentException(
                    "OfferHistoryStatus without snapshots must not have historical timestamps"
                );
            }

            if (publishedBefore) {

                throw new IllegalArgumentException(
                    "OfferHistoryStatus without snapshots cannot have published history"
                );
            }

        } else {

            /*
             * Existindo pelo menos um snapshot, os limites temporais
             * tornam-se obrigatórios.
             */
            firstDetectedAt =
                Objects.requireNonNull(
                    firstDetectedAt,
                    "OfferHistoryStatus firstDetectedAt must not be null when snapshots exist"
                );

            lastUpdatedAt =
                Objects.requireNonNull(
                    lastUpdatedAt,
                    "OfferHistoryStatus lastUpdatedAt must not be null when snapshots exist"
                );

            if (lastUpdatedAt.isBefore(
                firstDetectedAt
            )) {

                throw new IllegalArgumentException(
                    "OfferHistoryStatus lastUpdatedAt must not be before firstDetectedAt"
                );
            }
        }
    }

    /**
     * Uma oferta é recorrente quando o mesmo ASIN foi observado
     * em mais de um snapshot.
     */
    public boolean recurring() {

        return snapshotCount > 1;
    }

    /**
     * Calcula quanto tempo decorreu desde a primeira detecção até
     * um instante de referência.
     *
     * <p>A aplicação fornece explicitamente o instante de referência
     * para manter o cálculo determinístico e testável.</p>
     */
    public Optional<Duration> timeSinceFirstDetection(
        OffsetDateTime referenceTime
    ) {

        Objects.requireNonNull(
            referenceTime,
            "referenceTime must not be null"
        );

        if (firstDetectedAt == null) {

            return Optional.empty();
        }

        if (referenceTime.isBefore(
            firstDetectedAt
        )) {

            throw new IllegalArgumentException(
                "referenceTime must not be before firstDetectedAt"
            );
        }

        return Optional.of(
            Duration.between(
                firstDetectedAt,
                referenceTime
            )
        );
    }

    /**
     * Calcula quanto tempo decorreu desde a observação mais recente.
     */
    public Optional<Duration> timeSinceLastUpdate(
        OffsetDateTime referenceTime
    ) {

        Objects.requireNonNull(
            referenceTime,
            "referenceTime must not be null"
        );

        if (lastUpdatedAt == null) {

            return Optional.empty();
        }

        if (referenceTime.isBefore(
            lastUpdatedAt
        )) {

            throw new IllegalArgumentException(
                "referenceTime must not be before lastUpdatedAt"
            );
        }

        return Optional.of(
            Duration.between(
                lastUpdatedAt,
                referenceTime
            )
        );
    }
}

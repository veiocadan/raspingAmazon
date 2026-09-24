package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.RatingEvidence;
import com.raspingamazon.application.enrichment.contract.ReviewCountEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.enrichment.model.EvidenceType;
import com.raspingamazon.application.enrichment.port.OfferEvidenceRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implementação JDBC da persistência de provenance do enrichment.
 *
 * <p>Cada ProductEnrichmentResult produz uma linha para cada conceito
 * observado pelo enrichment:</p>
 *
 * <ul>
 *     <li>SELLER;</li>
 *     <li>DELIVERY;</li>
 *     <li>RATING;</li>
 *     <li>REVIEW_COUNT.</li>
 * </ul>
 *
 * <p>Rating e reviewCount também são persistidos quando indisponíveis.
 * Nesse caso raw_value e source_component podem ser null e
 * normalized_value recebe UNAVAILABLE, preservando a tentativa de
 * observação sem inventar dado.</p>
 *
 * <p>O repository apenas traduz contratos Java para SQL. Ele não
 * classifica vendedor, não decide precedência de fontes, não decide
 * elegibilidade e não interpreta HTML.</p>
 */
public final class OfferEvidenceJdbcRepository
    implements OfferEvidenceRepository {

    private static final String UNAVAILABLE =
        "UNAVAILABLE";

    private final Connection connection;

    public OfferEvidenceJdbcRepository(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "Connection must not be null"
            );
    }

    @Override
    public void insert(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException {

        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                "Offer snapshot id must be greater than zero"
            );
        }

        Objects.requireNonNull(
            enrichmentResult,
            "Enrichment result must not be null"
        );

        insertSellerEvidence(
            offerSnapshotId,
            enrichmentResult
        );

        insertDeliveryEvidence(
            offerSnapshotId,
            enrichmentResult
        );

        insertRatingEvidence(
            offerSnapshotId,
            enrichmentResult
        );

        insertReviewCountEvidence(
            offerSnapshotId,
            enrichmentResult
        );
    }

    private void insertSellerEvidence(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException {

        SellerEvidence evidence =
            enrichmentResult.sellerEvidence();

        insertEvidence(
            offerSnapshotId,
            EvidenceType.SELLER,
            evidence.rawValue(),
            evidence.sellerType().name(),
            enrichmentResult.source(),
            evidence.source(),
            enrichmentResult.enrichedAt()
        );
    }

    private void insertDeliveryEvidence(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException {

        DeliveryEvidence evidence =
            enrichmentResult.deliveryEvidence();

        insertEvidence(
            offerSnapshotId,
            EvidenceType.DELIVERY,
            evidence.rawValue(),
            evidence.deliveryType().name(),
            enrichmentResult.source(),
            evidence.source(),
            enrichmentResult.enrichedAt()
        );
    }

    private void insertRatingEvidence(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException {

        RatingEvidence evidence =
            enrichmentResult.ratingEvidence();

        String normalizedValue =
            evidence.rating() == null
                ? UNAVAILABLE
                : normalizeRating(
                    evidence.rating()
                );

        insertEvidence(
            offerSnapshotId,
            EvidenceType.RATING,
            evidence.rawValue(),
            normalizedValue,
            enrichmentResult.source(),
            evidence.source(),
            enrichmentResult.enrichedAt()
        );
    }

    private void insertReviewCountEvidence(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException {

        ReviewCountEvidence evidence =
            enrichmentResult.reviewCountEvidence();

        String normalizedValue =
            evidence.reviewCount() == null
                ? UNAVAILABLE
                : Long.toString(
                    evidence.reviewCount()
                );

        insertEvidence(
            offerSnapshotId,
            EvidenceType.REVIEW_COUNT,
            evidence.rawValue(),
            normalizedValue,
            enrichmentResult.source(),
            evidence.source(),
            enrichmentResult.enrichedAt()
        );
    }

    private String normalizeRating(
        double rating
    ) {

        return BigDecimal
            .valueOf(
                rating
            )
            .stripTrailingZeros()
            .toPlainString();
    }

    private void insertEvidence(
        long offerSnapshotId,
        EvidenceType evidenceType,
        String rawValue,
        String normalizedValue,
        String sourceAdapter,
        String sourceComponent,
        OffsetDateTime observedAt
    ) throws SQLException {

        String sql =
            """
            INSERT INTO offer_evidence (
                offer_snapshot_id,
                evidence_type,
                raw_value,
                normalized_value,
                source_adapter,
                source_component,
                observed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                offerSnapshotId
            );

            statement.setString(
                2,
                evidenceType.name()
            );

            if (rawValue == null) {

                statement.setObject(
                    3,
                    null
                );

            } else {

                statement.setString(
                    3,
                    rawValue
                );
            }

            statement.setString(
                4,
                normalizedValue
            );

            statement.setString(
                5,
                sourceAdapter
            );

            if (sourceComponent == null) {

                statement.setObject(
                    6,
                    null
                );

            } else {

                statement.setString(
                    6,
                    sourceComponent
                );
            }

            statement.setObject(
                7,
                observedAt
            );

            statement.executeUpdate();
        }
    }
}

package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.enrichment.model.EvidenceType;
import com.raspingamazon.application.enrichment.port.OfferEvidenceRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da persistência de provenance do enriquecimento.
 *
 * <p>Cada ProductEnrichmentResult produz atualmente duas linhas
 * em offer_evidence:</p>
 *
 * <ul>
 *     <li>uma evidência SELLER;</li>
 *     <li>uma evidência DELIVERY.</li>
 * </ul>
 *
 * <p>O repository apenas traduz contratos Java para SQL.
 * Ele não classifica vendedor, não decide elegibilidade e não
 * interpreta HTML.</p>
 */
public final class OfferEvidenceJdbcRepository
        implements OfferEvidenceRepository {

    private final Connection connection;

    /**
     * Cria o repository usando uma conexão existente.
     *
     * <p>Receber a Connection externamente será importante na etapa
     * 8.5-F, porque permitirá que snapshot, evidências e avaliação
     * participem da mesma transação.</p>
     */
    public OfferEvidenceJdbcRepository(
            Connection connection
    ) {
        this.connection =
                Objects.requireNonNull(
                        connection,
                        "Connection must not be null"
                );
    }

    /**
     * Persiste seller e delivery associados ao mesmo snapshot.
     */
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

        /*
         * Seller e delivery são gravados individualmente porque
         * representam evidências independentes.
         */
        insertSellerEvidence(
                offerSnapshotId,
                enrichmentResult
        );

        insertDeliveryEvidence(
                offerSnapshotId,
                enrichmentResult
        );
    }

    /**
     * Persiste a evidência referente ao vendedor.
     */
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

    /**
     * Persiste a evidência referente à entrega.
     */
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

    /**
     * Executa a inserção de uma evidência genérica.
     *
     * <p>O método privado centraliza o SQL para que seller e delivery
     * usem exatamente o mesmo formato de persistência.</p>
     */
    private void insertEvidence(
            long offerSnapshotId,
            EvidenceType evidenceType,
            String rawValue,
            String normalizedValue,
            String sourceAdapter,
            String sourceComponent,
            java.time.OffsetDateTime observedAt
    ) throws SQLException {

        String sql = """
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
                     connection.prepareStatement(sql)) {

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
package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcDealCandidateCorrelationTest {

    private static final String SOURCE =
        "OBSERVABILITY_CORRELATION_TEST";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-26T10:00:00Z"
        );

    @Test
    void shouldLinkOfferSnapshotIdempotently()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String asin =
            randomAsin();

        long runId =
            0L;

        long candidateId =
            0L;

        long productId =
            0L;

        long snapshotId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcDealCandidateRepositoryAdapter
                candidateRepository =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            DealCandidate candidate =
                candidateRepository.save(
                    candidate(
                        runId,
                        asin
                    )
                );

            candidateId =
                candidate.id();

            assertNull(
                findLinkedSnapshotId(
                    connection,
                    candidateId
                )
            );

            ProductRepository productRepository =
                new ProductRepository(
                    connection
                );

            productId =
                productRepository.insert(
                    asin,
                    "Produto para teste de correlação",
                    null,
                    productUrl(
                        asin
                    )
                );

            Product product =
                product(
                    productId,
                    asin
                );

            OfferSnapshotRepository snapshotRepository =
                new OfferSnapshotRepository(
                    connection
                );

            snapshotId =
                snapshotRepository.insert(
                    snapshot(
                        product,
                        COLLECTED_AT
                    )
                );

            candidateRepository.linkOfferSnapshot(
                candidateId,
                snapshotId
            );

            assertEquals(
                snapshotId,
                findLinkedSnapshotId(
                    connection,
                    candidateId
                )
            );

            /*
             * A mesma associação pode ser repetida por retry ou
             * reentrada sem produzir falha nem alterar a linhagem.
             */
            candidateRepository.linkOfferSnapshot(
                candidateId,
                snapshotId
            );

            assertEquals(
                snapshotId,
                findLinkedSnapshotId(
                    connection,
                    candidateId
                )
            );

        } finally {

            cleanup(
                config,
                candidateId,
                snapshotId,
                0L,
                productId,
                runId
            );
        }
    }

    @Test
    void shouldRejectOfferSnapshotReassignment()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String asin =
            randomAsin();

        long runId =
            0L;

        long candidateId =
            0L;

        long productId =
            0L;

        long firstSnapshotId =
            0L;

        long secondSnapshotId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcDealCandidateRepositoryAdapter
                candidateRepository =
                new JdbcDealCandidateRepositoryAdapter(
                    connection
                );

            DealCandidate candidate =
                candidateRepository.save(
                    candidate(
                        runId,
                        asin
                    )
                );

            candidateId =
                candidate.id();

            ProductRepository productRepository =
                new ProductRepository(
                    connection
                );

            productId =
                productRepository.insert(
                    asin,
                    "Produto para teste de imutabilidade",
                    null,
                    productUrl(
                        asin
                    )
                );

            Product product =
                product(
                    productId,
                    asin
                );

            OfferSnapshotRepository snapshotRepository =
                new OfferSnapshotRepository(
                    connection
                );

            firstSnapshotId =
                snapshotRepository.insert(
                    snapshot(
                        product,
                        COLLECTED_AT
                    )
                );

            secondSnapshotId =
                snapshotRepository.insert(
                    snapshot(
                        product,
                        COLLECTED_AT.plusMinutes(
                            1
                        )
                    )
                );

            candidateRepository.linkOfferSnapshot(
                candidateId,
                firstSnapshotId
            );

            assertEquals(
                firstSnapshotId,
                findLinkedSnapshotId(
                    connection,
                    candidateId
                )
            );

            long requestedSnapshotId =
                secondSnapshotId;

            IllegalStateException exception =
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        candidateRepository
                            .linkOfferSnapshot(
                                candidate.id(),
                                requestedSnapshotId
                            )
                );

            assertTrue(
                exception.getMessage()
                    .contains(
                        "is already linked to OfferSnapshot"
                    )
            );

            assertTrue(
                exception.getMessage()
                    .contains(
                        Long.toString(
                            firstSnapshotId
                        )
                    )
            );

            assertTrue(
                exception.getMessage()
                    .contains(
                        Long.toString(
                            secondSnapshotId
                        )
                    )
            );

            /*
             * A tentativa inválida não pode modificar a correlação
             * previamente estabelecida.
             */
            assertEquals(
                firstSnapshotId,
                findLinkedSnapshotId(
                    connection,
                    candidateId
                )
            );

        } finally {

            cleanup(
                config,
                candidateId,
                firstSnapshotId,
                secondSnapshotId,
                productId,
                runId
            );
        }
    }

    private long insertProcessingRun(
        Connection connection
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (?, ?, 'RUNNING', ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "correlation-test:"
                    + UUID.randomUUID()
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setObject(
                3,
                COLLECTED_AT
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingRun insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private DealCandidate candidate(
        long processingRunId,
        String asin
    ) {

        ParsedDeal parsedDeal =
            new ParsedDeal(
                asin,
                productUrl(
                    asin
                ),
                "Produto para correlação",
                "https://example.invalid/correlation.jpg",
                new BigDecimal(
                    "99.90"
                ),
                new BigDecimal(
                    "149.90"
                ),
                new BigDecimal(
                    "129.90"
                ),
                new BigDecimal(
                    "40.00"
                ),
                4.7,
                1234L,
                COLLECTED_AT,
                SOURCE
            );

        return new DealCandidate(
            null,
            processingRunId,
            parsedDeal
        );
    }

    private Product product(
        long productId,
        String asin
    ) {

        return new Product(
            productId,
            new Asin(
                asin
            ),
            "Produto para correlação",
            null,
            productUrl(
                asin
            )
        );
    }

    private OfferSnapshot snapshot(
        Product product,
        OffsetDateTime collectedAt
    ) {

        return new OfferSnapshot(
            null,
            product,
            collectedAt,
            new Money(
                new BigDecimal(
                    "99.90"
                )
            ),
            new Money(
                new BigDecimal(
                    "149.90"
                )
            ),
            new Money(
                new BigDecimal(
                    "129.90"
                )
            ),
            new Percentage(
                new BigDecimal(
                    "40.00"
                )
            ),
            4.7,
            1234L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            SOURCE,
            List.of()
        );
    }

    private Long findLinkedSnapshotId(
        Connection connection,
        long dealCandidateId
    ) throws Exception {

        String sql =
            """
            SELECT offer_snapshot_id
            FROM deal_candidate
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                dealCandidateId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "DealCandidate not found during correlation assertion: "
                            + dealCandidateId
                    );
                }

                long snapshotId =
                    resultSet.getLong(
                        "offer_snapshot_id"
                    );

                if (resultSet.wasNull()) {
                    return null;
                }

                return snapshotId;
            }
        }
    }

    private String randomAsin() {

        String suffix =
            UUID.randomUUID()
                .toString()
                .replace(
                    "-",
                    ""
                )
                .substring(
                    0,
                    8
                )
                .toUpperCase();

        return "B0"
            + suffix;
    }

    private String productUrl(
        String asin
    ) {

        return "https://www.amazon.com.br/dp/"
            + asin;
    }

    private void cleanup(
        ApplicationConfig config,
        long candidateId,
        long firstSnapshotId,
        long secondSnapshotId,
        long productId,
        long runId
    ) {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * DealCandidate precisa sair antes dos snapshots porque
             * V20 introduziu a FK candidate -> snapshot.
             */
            deleteById(
                connection,
                "deal_candidate",
                candidateId
            );

            deleteById(
                connection,
                "offer_snapshot",
                secondSnapshotId
            );

            deleteById(
                connection,
                "offer_snapshot",
                firstSnapshotId
            );

            deleteById(
                connection,
                "product",
                productId
            );

            deleteById(
                connection,
                "processing_run",
                runId
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean DealCandidate correlation test data",
                exception
            );
        }
    }

    private void deleteById(
        Connection connection,
        String table,
        long id
    ) throws Exception {

        if (id <= 0) {
            return;
        }

        String sql =
            "DELETE FROM "
                + table
                + " WHERE id = ?";

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            statement.executeUpdate();
        }
    }
}

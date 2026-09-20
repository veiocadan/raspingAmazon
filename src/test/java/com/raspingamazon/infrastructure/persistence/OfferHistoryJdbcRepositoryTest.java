package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferHistoryJdbcRepositoryTest {

    private static final Asin ASIN =
        new Asin(
            "B0HIST1101"
        );

    @Test
    void shouldQueryHistoricalObservationsInDeterministicOrder()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * Todo o cenário deste teste permanece dentro de uma
             * transação descartável.
             *
             * Não precisamos executar DELETE manual ao final.
             */
            connection.setAutoCommit(
                false
            );

            try {

                ProductRepository productRepository =
                    new ProductRepository(
                        connection
                    );

                long productId =
                    productRepository.insert(
                        ASIN.value(),
                        "Produto histórico da FASE 11",
                        null,
                        "https://example.invalid/history-product"
                    );

                Product product =
                    new Product(
                        productId,
                        ASIN,
                        "Produto histórico da FASE 11",
                        null,
                        "https://example.invalid/history-product"
                    );

                OffsetDateTime firstCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T10:00:00-03:00"
                    );

                OffsetDateTime secondCollectedAt =
                    OffsetDateTime.parse(
                        "2026-09-20T13:00:00-03:00"
                    );

                PaymentCondition firstCashCondition =
                    new PaymentCondition(
                        PaymentConditionType.CASH,
                        new Money(
                            new BigDecimal(
                                "90.00"
                            )
                        ),
                        new Percentage(
                            new BigDecimal(
                                "10"
                            )
                        ),
                        null,
                        null,
                        null,
                        null,
                        List.of(
                            PaymentMethod.PIX
                        )
                    );

                PaymentCondition secondCashCondition =
                    new PaymentCondition(
                        PaymentConditionType.CASH,
                        new Money(
                            new BigDecimal(
                                "80.00"
                            )
                        ),
                        new Percentage(
                            new BigDecimal(
                                "20"
                            )
                        ),
                        null,
                        null,
                        null,
                        null,
                        List.of(
                            PaymentMethod.PIX,
                            PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                        )
                    );

                OfferSnapshot firstSnapshot =
                    new OfferSnapshot(
                        null,
                        product,
                        firstCollectedAt,
                        new Money(
                            new BigDecimal(
                                "100.00"
                            )
                        ),
                        null,
                        null,
                        new Percentage(
                            new BigDecimal(
                                "20"
                            )
                        ),
                        4.6,
                        1000L,
                        "Amazon.com.br",
                        "Amazon",
                        SellerType.AMAZON,
                        DeliveryType.AMAZON,
                        "history-test",
                        List.of(
                            firstCashCondition
                        )
                    );

                OfferSnapshot secondSnapshot =
                    new OfferSnapshot(
                        null,
                        product,
                        secondCollectedAt,
                        new Money(
                            new BigDecimal(
                                "90.00"
                            )
                        ),
                        null,
                        null,
                        new Percentage(
                            new BigDecimal(
                                "35"
                            )
                        ),
                        4.6,
                        1000L,
                        "Amazon.com.br",
                        "Amazon",
                        SellerType.AMAZON,
                        DeliveryType.AMAZON,
                        "history-test",
                        List.of(
                            secondCashCondition
                        )
                    );

                OfferSnapshotRepository snapshotRepository =
                    new OfferSnapshotRepository(
                        connection
                    );

                long firstSnapshotId =
                    snapshotRepository.insert(
                        firstSnapshot
                    );

                long secondSnapshotId =
                    snapshotRepository.insert(
                        secondSnapshot
                    );

                OfferPaymentConditionRepository
                    paymentConditionRepository =
                    new OfferPaymentConditionRepository(
                        connection
                    );

                paymentConditionRepository.insert(
                    firstSnapshotId,
                    firstCashCondition
                );

                paymentConditionRepository.insert(
                    secondSnapshotId,
                    secondCashCondition
                );

                OfferHistoryJdbcRepository repository =
                    new OfferHistoryJdbcRepository(
                        connection
                    );

                /*
                 * -------------------------------------------------
                 * HISTÓRICO COMPLETO
                 * -------------------------------------------------
                 */
                List<HistoricalOfferObservation> history =
                    repository.findHistoryByAsin(
                        ASIN
                    );

                assertEquals(
                    2,
                    history.size()
                );

                HistoricalOfferObservation first =
                    history.get(
                        0
                    );

                HistoricalOfferObservation second =
                    history.get(
                        1
                    );

                assertEquals(
                    firstSnapshotId,
                    first.snapshotId()
                );

                assertEquals(
                    secondSnapshotId,
                    second.snapshotId()
                );

                assertEquals(
                    ASIN,
                    first.asin()
                );

                assertEquals(
                    firstCollectedAt.toInstant(),
                    first.collectedAt()
                        .toInstant()
                );

                assertEquals(
                    secondCollectedAt.toInstant(),
                    second.collectedAt()
                        .toInstant()
                );

                assertBigDecimalEquals(
                    "100.00",
                    first.currentPrice()
                        .amount()
                );

                assertBigDecimalEquals(
                    "90.00",
                    second.currentPrice()
                        .amount()
                );

                assertBigDecimalEquals(
                    "20",
                    first.soldPercentage()
                        .value()
                );

                assertBigDecimalEquals(
                    "35",
                    second.soldPercentage()
                        .value()
                );

                assertEquals(
                    "history-test",
                    first.source()
                );

                /*
                 * -------------------------------------------------
                 * CONDIÇÕES COMERCIAIS HISTÓRICAS
                 * -------------------------------------------------
                 */
                assertEquals(
                    1,
                    first.paymentConditions()
                        .size()
                );

                PaymentCondition firstCondition =
                    first.paymentConditions()
                        .get(
                            0
                        );

                assertEquals(
                    PaymentConditionType.CASH,
                    firstCondition.type()
                );

                assertBigDecimalEquals(
                    "10",
                    firstCondition
                        .discountPercentage()
                        .value()
                );

                assertEquals(
                    List.of(
                        PaymentMethod.PIX
                    ),
                    firstCondition.paymentMethods()
                );

                assertEquals(
                    1,
                    second.paymentConditions()
                        .size()
                );

                PaymentCondition secondCondition =
                    second.paymentConditions()
                        .get(
                            0
                        );

                assertBigDecimalEquals(
                    "20",
                    secondCondition
                        .discountPercentage()
                        .value()
                );

                assertEquals(
                    List.of(
                        PaymentMethod.PIX,
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                    ),
                    secondCondition.paymentMethods()
                );

                /*
                 * -------------------------------------------------
                 * PRIMEIRA OBSERVAÇÃO
                 * -------------------------------------------------
                 */
                HistoricalOfferObservation firstFound =
                    repository.findFirstByAsin(
                        ASIN
                    ).orElseThrow();

                assertEquals(
                    firstSnapshotId,
                    firstFound.snapshotId()
                );

                /*
                 * -------------------------------------------------
                 * ÚLTIMA OBSERVAÇÃO
                 * -------------------------------------------------
                 */
                HistoricalOfferObservation latestFound =
                    repository.findLatestByAsin(
                        ASIN
                    ).orElseThrow();

                assertEquals(
                    secondSnapshotId,
                    latestFound.snapshotId()
                );

                /*
                 * -------------------------------------------------
                 * OBSERVAÇÃO ANTERIOR
                 * -------------------------------------------------
                 */
                HistoricalOfferObservation previousFound =
                    repository.findPreviousByAsin(
                        ASIN,
                        secondCollectedAt
                    ).orElseThrow();

                assertEquals(
                    firstSnapshotId,
                    previousFound.snapshotId()
                );

                /*
                 * Não existe observação estritamente anterior
                 * à primeira coleta.
                 */
                assertTrue(
                    repository.findPreviousByAsin(
                        ASIN,
                        firstCollectedAt
                    ).isEmpty()
                );

                /*
                 * -------------------------------------------------
                 * CONTAGEM / RECORRÊNCIA FUTURA
                 * -------------------------------------------------
                 */
                assertEquals(
                    2L,
                    repository.countByAsin(
                        ASIN
                    )
                );

                /*
                 * -------------------------------------------------
                 * ASIN DESCONHECIDO
                 * -------------------------------------------------
                 */
                Asin unknownAsin =
                    new Asin(
                        "B0HIST1199"
                    );

                assertTrue(
                    repository.findHistoryByAsin(
                        unknownAsin
                    ).isEmpty()
                );

                assertTrue(
                    repository.findFirstByAsin(
                        unknownAsin
                    ).isEmpty()
                );

                assertTrue(
                    repository.findLatestByAsin(
                        unknownAsin
                    ).isEmpty()
                );

                assertEquals(
                    0L,
                    repository.countByAsin(
                        unknownAsin
                    )
                );

                assertFalse(
                    history.isEmpty()
                );

            } finally {

                /*
                 * O teste não deixa estado permanente no PostgreSQL.
                 */
                connection.rollback();
            }
        }
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {

        assertEquals(
            0,
            new BigDecimal(
                expected
            ).compareTo(
                actual
            )
        );
    }
}

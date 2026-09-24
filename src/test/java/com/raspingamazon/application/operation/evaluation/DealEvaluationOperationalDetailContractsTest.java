package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DealEvaluationOperationalDetailContractsTest {

    private static final OffsetDateTime TIME =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    @Test
    void shouldCreateOperationalRuleResult() {

        OperationalEvaluationRuleResult result =
            new OperationalEvaluationRuleResult(
                0,
                "SELLER_IS_AMAZON",
                true,
                "AMAZON",
                "AMAZON",
                null
            );

        assertEquals(
            0,
            result.ruleOrder()
        );

        assertEquals(
            "SELLER_IS_AMAZON",
            result.ruleCode()
        );
    }

    @Test
    void shouldRejectNegativeRuleOrder() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalEvaluationRuleResult(
                -1,
                "SELLER_IS_AMAZON",
                true,
                "AMAZON",
                "AMAZON",
                null
            )
        );
    }

    @Test
    void shouldRejectNegativeScoreFactorOrder() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalScoreFactorResult(
                -1,
                "RATING",
                "AVAILABLE",
                new BigDecimal(
                    "4.5"
                ),
                new BigDecimal(
                    "90"
                ),
                new BigDecimal(
                    "20"
                ),
                new BigDecimal(
                    "18"
                )
            )
        );
    }

    @Test
    void shouldRejectUnorderedDetailCollections() {

        List<OperationalEvaluationRuleResult> unorderedRules =
            List.of(
                rule(
                    1
                ),
                rule(
                    0
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> detail(
                unorderedRules,
                List.of()
            )
        );

        List<OperationalScoreFactorResult> unorderedFactors =
            List.of(
                factor(
                    1
                ),
                factor(
                    0
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> detail(
                List.of(),
                unorderedFactors
            )
        );
    }

    @Test
    void shouldDefensivelyCopyDetailCollections() {

        ArrayList<OperationalEvaluationRuleResult> rules =
            new ArrayList<>();

        rules.add(
            rule(
                0
            )
        );

        DealEvaluationDetail detail =
            detail(
                rules,
                List.of(
                    factor(
                        0
                    )
                )
            );

        rules.clear();

        assertEquals(
            1,
            detail.ruleResults()
                .size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> detail.ruleResults()
                .clear()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> detail.scoreFactors()
                .clear()
        );
    }

    private DealEvaluationDetail detail(
        List<OperationalEvaluationRuleResult> rules,
        List<OperationalScoreFactorResult> factors
    ) {

        DealEvaluationSummary summary =
            new DealEvaluationSummary(
                30L,
                20L,
                10L,
                new Asin(
                    "B0DET14001"
                ),
                "Produto operacional",
                Money.of(
                    "99.90"
                ),
                true,
                null,
                new BigDecimal(
                    "18.00"
                ),
                new BigDecimal(
                    "12.50"
                ),
                TIME.minusMinutes(
                    5
                ),
                TIME
            );

        return new DealEvaluationDetail(
            summary,
            "https://www.amazon.com.br/dp/B0DET14001",
            Money.of(
                "129.90"
            ),
            Money.of(
                "119.90"
            ),
            new BigDecimal(
                "42.00"
            ),
            4.5,
            800L,
            "Amazon.com.br",
            "Amazon.com.br",
            "TEST",
            "TEST_ELIGIBILITY",
            "TEST_FILTER",
            "TEST_SCORE",
            rules,
            factors,
            "TEST_MOMENTUM",
            null
        );
    }

    private OperationalEvaluationRuleResult rule(
        int order
    ) {

        return new OperationalEvaluationRuleResult(
            order,
            "RULE_" + order,
            true,
            "OBSERVED",
            "EXPECTED",
            null
        );
    }

    private OperationalScoreFactorResult factor(
        int order
    ) {

        return new OperationalScoreFactorResult(
            order,
            "FACTOR_" + order,
            "AVAILABLE",
            BigDecimal.ONE,
            new BigDecimal(
                "100"
            ),
            new BigDecimal(
                "10"
            ),
            new BigDecimal(
                "10"
            )
        );
    }
}

package com.raspingamazon.domain.scoring;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DealEvaluationRankingTest {

    private final DealEvaluationRanking ranking =
        new DealEvaluationRanking();

    @Test
    void shouldOrderScoredEvaluationsByScoreDescending() {

        DealEvaluation lower =
            scoredEvaluation(
                "B000000001",
                "40.0000"
            );

        DealEvaluation higher =
            scoredEvaluation(
                "B000000002",
                "70.0000"
            );

        DealEvaluation middle =
            scoredEvaluation(
                "B000000003",
                "55.0000"
            );

        List<DealEvaluation> result =
            ranking.rank(
                List.of(
                    lower,
                    higher,
                    middle
                )
            );

        assertEquals(
            List.of(
                higher,
                middle,
                lower
            ),
            result
        );
    }

    @Test
    void shouldUseAsinAscendingAsDeterministicTieBreaker() {

        DealEvaluation second =
            scoredEvaluation(
                "B000000002",
                "50.0000"
            );

        DealEvaluation first =
            scoredEvaluation(
                "B000000001",
                "50.0000"
            );

        DealEvaluation third =
            scoredEvaluation(
                "B000000003",
                "50.0000"
            );

        List<DealEvaluation> result =
            ranking.rank(
                List.of(
                    second,
                    third,
                    first
                )
            );

        assertEquals(
            "B000000001",
            asin(
                result.get(0)
            )
        );

        assertEquals(
            "B000000002",
            asin(
                result.get(1)
            )
        );

        assertEquals(
            "B000000003",
            asin(
                result.get(2)
            )
        );
    }

    @Test
    void shouldExcludeEvaluationsWithoutScore() {

        DealEvaluation scored =
            scoredEvaluation(
                "B000000001",
                "50.0000"
            );

        DealEvaluation rejected =
            rejectedEvaluation(
                "B000000002"
            );

        List<DealEvaluation> result =
            ranking.rank(
                List.of(
                    rejected,
                    scored
                )
            );

        assertEquals(
            1,
            result.size()
        );

        assertEquals(
            scored,
            result.get(0)
        );
    }

    @Test
    void shouldNotModifyOriginalList() {

        DealEvaluation lower =
            scoredEvaluation(
                "B000000001",
                "40.0000"
            );

        DealEvaluation higher =
            scoredEvaluation(
                "B000000002",
                "70.0000"
            );

        List<DealEvaluation> original =
            new ArrayList<>(
                List.of(
                    lower,
                    higher
                )
            );

        List<DealEvaluation> result =
            ranking.rank(
                original
            );

        assertEquals(
            List.of(
                lower,
                higher
            ),
            original
        );

        assertEquals(
            List.of(
                higher,
                lower
            ),
            result
        );
    }

    @Test
    void shouldReturnImmutableRanking() {

        List<DealEvaluation> result =
            ranking.rank(
                List.of(
                    scoredEvaluation(
                        "B000000001",
                        "50.0000"
                    )
                )
            );

        assertThrows(
            UnsupportedOperationException.class,
            () -> result.clear()
        );
    }

    @Test
    void shouldRejectNullEvaluationList() {

        assertThrows(
            NullPointerException.class,
            () -> ranking.rank(
                null
            )
        );
    }

    @Test
    void shouldRejectListContainingNullEvaluation() {

        List<DealEvaluation> evaluations =
            new ArrayList<>();

        evaluations.add(
            scoredEvaluation(
                "B000000001",
                "50.0000"
            )
        );

        evaluations.add(
            null
        );

        assertThrows(
            NullPointerException.class,
            () -> ranking.rank(
                evaluations
            )
        );
    }

    private DealEvaluation scoredEvaluation(
        String asin,
        String score
    ) {

        BigDecimal scoreValue =
            new BigDecimal(
                score
            );

        /*
         * Para estes testes de ranking não importa qual fator
         * comercial produziu a pontuação.
         *
         * Utilizamos um único fator cuja contribuição é exatamente
         * igual ao score informado.
         *
         * Os valores usados nos testes permanecem <= 100, portanto
         * respeitam as invariantes de ScoreFactorResult e ScoreResult.
         */
        ScoreFactorResult factor =
            ScoreFactorResult.available(
                ScoreFactorCode.SOLD_PERCENTAGE,
                scoreValue,
                scoreValue,
                new BigDecimal("100"),
                scoreValue
            );

        return new DealEvaluation(
            null,
            snapshot(
                asin
            ),
            true,
            null,
            "AMAZON_SELLER_DELIVERY_V1",
            "COMMERCIAL_FILTER_V1",
            passedRules(),
            scoreValue,
            "SCORE_TEST",
            List.of(
                factor
            ),
            null,
            null,
            OffsetDateTime.parse(
                "2026-09-20T18:00:00-03:00"
            )
        );
    }

    private DealEvaluation rejectedEvaluation(
        String asin
    ) {

        List<EvaluationRuleResult> rules =
            List.of(
                EvaluationRuleResult.failed(
                    "SELLER_IS_AMAZON",
                    "THIRD_PARTY",
                    "AMAZON",
                    RejectionReason.SELLER_THIRD_PARTY
                )
            );

        return new DealEvaluation(
            null,
            snapshot(
                asin
            ),
            false,
            RejectionReason.SELLER_THIRD_PARTY,
            "AMAZON_SELLER_DELIVERY_V1",
            "COMMERCIAL_FILTER_V1",
            rules,
            null,
            null,
            null,
            null,
            OffsetDateTime.parse(
                "2026-09-20T18:00:00-03:00"
            )
        );
    }

    private OfferSnapshot snapshot(
        String asin
    ) {

        Product product =
            new Product(
                null,
                new Asin(
                    asin
                ),
                "Produto " + asin,
                null,
                "https://example.invalid/"
                    + asin
            );

        return new OfferSnapshot(
            null,
            product,
            OffsetDateTime.parse(
                "2026-09-20T17:55:00-03:00"
            ),
            Money.of(
                "99.90"
            ),
            null,
            null,
            null,
            4.7,
            1000L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "TEST",
            List.of()
        );
    }

    private List<EvaluationRuleResult> passedRules() {

        return List.of(
            EvaluationRuleResult.passed(
                "SELLER_IS_AMAZON",
                "AMAZON",
                "AMAZON"
            )
        );
    }

    private String asin(
        DealEvaluation evaluation
    ) {

        return evaluation
            .offerSnapshot()
            .product()
            .asin()
            .value();
    }
}

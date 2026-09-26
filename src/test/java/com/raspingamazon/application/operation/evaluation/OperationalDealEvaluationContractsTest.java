package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalDealEvaluationContractsTest {

    private static final OffsetDateTime FIRST_TIME =
        OffsetDateTime.parse(
            "2026-09-24T10:00:00-03:00"
        );

    private static final OffsetDateTime SECOND_TIME =
        OffsetDateTime.parse(
            "2026-09-24T11:00:00-03:00"
        );

    @Test
    void shouldCreateValidCursor() {

        DealEvaluationCursor cursor =
            new DealEvaluationCursor(
                SECOND_TIME,
                20L
            );

        assertEquals(
            SECOND_TIME,
            cursor.evaluatedAt()
        );

        assertEquals(
            20L,
            cursor.evaluationId()
        );
    }

    @Test
    void shouldRejectInvalidCursorEvaluationId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealEvaluationCursor(
                SECOND_TIME,
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealEvaluationCursor(
                SECOND_TIME,
                -1L
            )
        );
    }

    @Test
    void shouldCreateDefaultFirstPageCriteria() {

        DealEvaluationSearchCriteria criteria =
            DealEvaluationSearchCriteria.firstPage();

        assertNull(
            criteria.eligible()
        );

        assertNull(
            criteria.asin()
        );

        assertNull(
            criteria.after()
        );

        assertEquals(
            DealEvaluationSearchCriteria.DEFAULT_LIMIT,
            criteria.limit()
        );
    }

    @Test
    void shouldRejectInvalidPageLimit() {

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                null,
                null,
                null,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                null,
                null,
                null,
                DealEvaluationSearchCriteria.MAX_LIMIT + 1
            )
        );
    }

    @Test
    void shouldRejectScoreOutsideAllowedRange() {

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                null,
                new BigDecimal(
                    "-0.01"
                ),
                null,
                50
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                null,
                null,
                new BigDecimal(
                    "100.01"
                ),
                50
            )
        );
    }

    @Test
    void shouldRejectMinimumScoreGreaterThanMaximumScore() {

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                null,
                new BigDecimal(
                    "80"
                ),
                new BigDecimal(
                    "70"
                ),
                50
            )
        );
    }

    @Test
    void shouldRejectInvertedEvaluationDateRange() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealEvaluationSearchCriteria(
                null,
                null,
                null,
                null,
                SECOND_TIME,
                FIRST_TIME,
                null,
                50
            )
        );
    }

    @Test
    void shouldRejectScoreFiltersForIneligibleOnlySearch() {

        assertThrows(
            IllegalArgumentException.class,
            () -> criteria(
                false,
                new BigDecimal(
                    "10"
                ),
                null,
                50
            )
        );
    }

    @Test
    void shouldAcceptEligibleSummary() {

        DealEvaluationSummary summary =
            summary(
                20L,
                SECOND_TIME,
                true,
                null,
                new BigDecimal(
                    "87.50"
                )
            );

        assertTrue(
            summary.eligible()
        );

        assertEquals(
            new BigDecimal(
                "87.50"
            ),
            summary.score()
        );

        assertNull(
            summary.rejectionReason()
        );
    }

    @Test
    void shouldAcceptIneligibleSummary() {

        DealEvaluationSummary summary =
            summary(
                20L,
                SECOND_TIME,
                false,
                RejectionReason.RATING_BELOW_MINIMUM,
                null
            );

        assertFalse(
            summary.eligible()
        );

        assertEquals(
            RejectionReason.RATING_BELOW_MINIMUM,
            summary.rejectionReason()
        );

        assertNull(
            summary.score()
        );
    }

    @Test
    void shouldRejectInconsistentEligibilitySummary() {

        assertThrows(
            IllegalArgumentException.class,
            () -> summary(
                20L,
                SECOND_TIME,
                true,
                RejectionReason.RATING_BELOW_MINIMUM,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> summary(
                20L,
                SECOND_TIME,
                false,
                null,
                null
            )
        );
    }

    @Test
    void shouldRejectScoreForIneligibleSummary() {

        assertThrows(
            IllegalArgumentException.class,
            () -> summary(
                20L,
                SECOND_TIME,
                false,
                RejectionReason.RATING_BELOW_MINIMUM,
                new BigDecimal(
                    "50"
                )
            )
        );
    }

    @Test
    void shouldCreateOrderedPageWithNextCursor() {

        DealEvaluationSummary newest =
            summary(
                20L,
                SECOND_TIME,
                true,
                null,
                new BigDecimal(
                    "90"
                )
            );

        DealEvaluationSummary oldest =
            summary(
                10L,
                FIRST_TIME,
                true,
                null,
                new BigDecimal(
                    "80"
                )
            );

        DealEvaluationCursor nextCursor =
            new DealEvaluationCursor(
                oldest.evaluatedAt(),
                oldest.evaluationId()
            );

        DealEvaluationPage page =
            new DealEvaluationPage(
                List.of(
                    newest,
                    oldest
                ),
                nextCursor
            );

        assertEquals(
            2,
            page.items().size()
        );

        assertTrue(
            page.hasNextPage()
        );

        assertEquals(
            nextCursor,
            page.nextCursor()
        );
    }

    @Test
    void shouldRejectUnorderedPage() {

        DealEvaluationSummary older =
            summary(
                10L,
                FIRST_TIME,
                true,
                null,
                new BigDecimal(
                    "80"
                )
            );

        DealEvaluationSummary newer =
            summary(
                20L,
                SECOND_TIME,
                true,
                null,
                new BigDecimal(
                    "90"
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealEvaluationPage(
                List.of(
                    older,
                    newer
                ),
                null
            )
        );
    }

    @Test
    void shouldRejectCursorThatDoesNotRepresentLastItem() {

        DealEvaluationSummary newest =
            summary(
                20L,
                SECOND_TIME,
                true,
                null,
                new BigDecimal(
                    "90"
                )
            );

        DealEvaluationSummary oldest =
            summary(
                10L,
                FIRST_TIME,
                true,
                null,
                new BigDecimal(
                    "80"
                )
            );

        DealEvaluationCursor invalidCursor =
            new DealEvaluationCursor(
                SECOND_TIME,
                20L
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealEvaluationPage(
                List.of(
                    newest,
                    oldest
                ),
                invalidCursor
            )
        );
    }

    @Test
    void shouldDefensivelyCopyPageItems() {

        ArrayList<DealEvaluationSummary> mutableItems =
            new ArrayList<>();

        mutableItems.add(
            summary(
                20L,
                SECOND_TIME,
                true,
                null,
                new BigDecimal(
                    "90"
                )
            )
        );

        DealEvaluationPage page =
            new DealEvaluationPage(
                mutableItems,
                null
            );

        mutableItems.clear();

        assertEquals(
            1,
            page.items().size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> page.items().clear()
        );
    }

    private DealEvaluationSearchCriteria criteria(
        Boolean eligible,
        BigDecimal minimumScore,
        BigDecimal maximumScore,
        int limit
    ) {

        return new DealEvaluationSearchCriteria(
            eligible,
            null,
            minimumScore,
            maximumScore,
            null,
            null,
            null,
            limit
        );
    }

    private DealEvaluationSummary summary(
        long evaluationId,
        OffsetDateTime evaluatedAt,
        boolean eligible,
        RejectionReason rejectionReason,
        BigDecimal score
    ) {

        return new DealEvaluationSummary(
            evaluationId,
            evaluationId + 100L,
            evaluationId + 200L,
            new Asin(
                "B0TEST0001"
            ),
            "Produto operacional",
            Money.of(
                "99.90"
            ),
            eligible,
            rejectionReason,
            score,
            new BigDecimal(
                "12.50"
            ),
            evaluatedAt.minusMinutes(
                5
            ),
            evaluatedAt
        );
    }
}

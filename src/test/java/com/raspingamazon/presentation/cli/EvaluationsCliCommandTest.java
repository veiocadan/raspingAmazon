package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationCursor;
import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.evaluation.OperationalEvaluationRuleResult;
import com.raspingamazon.application.operation.evaluation.OperationalMomentumAudit;
import com.raspingamazon.application.operation.evaluation.OperationalScoreFactorResult;
import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalDetailQueryPort;
import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationsCliCommandTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:05:00-03:00"
        );

    @Test
    void shouldListEvaluationsWithDefaultCriteria() {

        AtomicReference<DealEvaluationSearchCriteria> received =
            new AtomicReference<>();

        EvaluationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new DealEvaluationPage(
                        List.of(),
                        null
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "list"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertEquals(
            DealEvaluationSearchCriteria.DEFAULT_LIMIT,
            received.get()
                .limit()
        );

        assertNull(
            received.get()
                .eligible()
        );

        assertNull(
            received.get()
                .after()
        );

        assertTrue(
            console.stdout()
                .startsWith(
                    "EVALUATION_ID\t"
                )
        );
    }

    @Test
    void shouldParseAllListFilters() {

        AtomicReference<DealEvaluationSearchCriteria> received =
            new AtomicReference<>();

        EvaluationsCliCommand command =
            command(
                criteria -> {

                    received.set(
                        criteria
                    );

                    return new DealEvaluationPage(
                        List.of(),
                        null
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        command.execute(
            List.of(
                "list",
                "--eligible",
                "true",
                "--asin",
                "B0CLI14001",
                "--min-score",
                "70.5000",
                "--max-score",
                "95.0000",
                "--from",
                "2026-09-01T00:00:00-03:00",
                "--until",
                "2026-09-24T23:59:59-03:00",
                "--after-at",
                "2026-09-20T12:00:00-03:00",
                "--after-id",
                "123",
                "--limit",
                "25"
            ),
            console.out(),
            console.err()
        );

        DealEvaluationSearchCriteria expected =
            new DealEvaluationSearchCriteria(
                Boolean.TRUE,
                new Asin(
                    "B0CLI14001"
                ),
                new BigDecimal(
                    "70.5000"
                ),
                new BigDecimal(
                    "95.0000"
                ),
                OffsetDateTime.parse(
                    "2026-09-01T00:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T23:59:59-03:00"
                ),
                new DealEvaluationCursor(
                    OffsetDateTime.parse(
                        "2026-09-20T12:00:00-03:00"
                    ),
                    123L
                ),
                25
            );

        assertEquals(
            expected,
            received.get()
        );
    }

    @Test
    void shouldRenderEvaluationAndNextCursor() {

        DealEvaluationSummary summary =
            summary();

        DealEvaluationCursor cursor =
            new DealEvaluationCursor(
                EVALUATED_AT,
                123L
            );

        EvaluationsCliCommand command =
            command(
                criteria ->
                    new DealEvaluationPage(
                        List.of(
                            summary
                        ),
                        cursor
                    )
            );

        TestConsole console =
            new TestConsole();

        command.execute(
            List.of(
                "list"
            ),
            console.out(),
            console.err()
        );

        String output =
            console.stdout();

        assertTrue(
            output.contains(
                "123\t456\t789\tB0CLI14002\ttrue"
            )
        );

        assertTrue(
            output.contains(
                "\t84.2500\t0.7500\t129.90\t-\t"
            )
        );

        assertTrue(
            output.contains(
                "Produto com quebra"
            )
        );

        assertTrue(
            output.contains(
                "NEXT_CURSOR\t"
                    + EVALUATED_AT
                    + "\t123"
            )
        );
    }

    @Test
    void shouldRequireBothCursorComponents() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--after-id",
                    "10"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--after-at",
                    "2026-09-24T20:00:00-03:00"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectDuplicateOption() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "list",
                        "--limit",
                        "10",
                        "--limit",
                        "20"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "duplicate option"
                )
        );
    }

    @Test
    void shouldRejectUnknownOptionAndMissingValue() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--unknown",
                    "x"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectInvalidScalarValues() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--eligible",
                    "maybe"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--min-score",
                    "abc"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--from",
                    "not-a-date"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--limit",
                    "zero"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldExposeCriteriaValidationAsUsageError() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "list",
                    "--eligible",
                    "false",
                    "--min-score",
                    "50"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldPrintEvaluationsHelp() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "help"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            console.stdout()
                .contains(
                    "rasping-amazon evaluations list"
                )
        );

        assertTrue(
            console.stdout()
                .contains(
                    "rasping-amazon evaluations show <evaluation-id>"
                )
        );
    }

    @Test
    void shouldRejectUnknownEvaluationAction() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "delete"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "unknown evaluations action"
                )
        );
    }

    @Test
    void shouldShowPersistedEvaluationDetail() {

        AtomicLong receivedId =
            new AtomicLong();

        DealEvaluationDetail detail =
            detailedEvaluation();

        EvaluationsCliCommand command =
            command(
                emptyListPort(),
                evaluationId -> {

                    receivedId.set(
                        evaluationId
                    );

                    return Optional.of(
                        detail
                    );
                }
            );

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "show",
                    "123"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertEquals(
            123L,
            receivedId.get()
        );

        String output =
            console.stdout();

        assertTrue(
            output.contains(
                "EVALUATION\n"
            )
        );

        assertTrue(
            output.contains(
                "EVALUATION_ID\t123"
            )
        );

        assertTrue(
            output.contains(
                "PRODUCT_URL\thttps://example.invalid/product"
            )
        );

        assertTrue(
            output.contains(
                "ELIGIBILITY_POLICY_VERSION\tELIGIBILITY_V1"
            )
        );

        assertTrue(
            output.contains(
                "FILTER_PROFILE_VERSION\tCOMMERCIAL_FILTER_V1"
            )
        );

        assertTrue(
            output.contains(
                "SCORE_VERSION\tSCORE_V1"
            )
        );

        assertTrue(
            output.contains(
                "RULE_RESULTS"
            )
        );

        assertTrue(
            output.contains(
                "1\tMIN_RATING\ttrue\t4.8\t4.3\t-"
            )
        );

        assertTrue(
            output.contains(
                "SCORE_FACTORS"
            )
        );

        assertTrue(
            output.contains(
                "1\tDISCOUNT\tAPPLIED\t25.00\t0.7500\t0.4000\t30.0000"
            )
        );

        assertTrue(
            output.contains(
                "MOMENTUM_AUDIT"
            )
        );

        assertTrue(
            output.contains(
                "PRESENT\ttrue"
            )
        );

        assertTrue(
            output.contains(
                "AUDIT_ID\t9001"
            )
        );

        assertTrue(
            output.contains(
                "PREVIOUS_OFFER_SNAPSHOT_ID\t455"
            )
        );
    }

    @Test
    void shouldRenderAbsentOptionalEvaluationDetailValues() {

        DealEvaluationDetail detail =
            new DealEvaluationDetail(
                summary(),
                "https://example.invalid/product",
                null,
                null,
                null,
                null,
                null,
                "Amazon",
                "Amazon",
                "amazon-deals",
                "ELIGIBILITY_V1",
                null,
                null,
                List.of(),
                List.of(),
                null,
                null
            );

        EvaluationsCliCommand command =
            command(
                emptyListPort(),
                evaluationId ->
                    Optional.of(
                        detail
                    )
            );

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "show",
                    "123"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        String output =
            console.stdout();

        assertTrue(
            output.contains(
                "BASIS_PRICE\t-"
            )
        );

        assertTrue(
            output.contains(
                "RATING\t-"
            )
        );

        assertTrue(
            output.contains(
                "FILTER_PROFILE_VERSION\t-"
            )
        );

        assertTrue(
            output.contains(
                "SCORE_VERSION\t-"
            )
        );

        assertTrue(
            output.contains(
                "MOMENTUM_VERSION\t-"
            )
        );

        assertTrue(
            output.contains(
                "MOMENTUM_AUDIT\nPRESENT\tfalse"
            )
        );
    }

    @Test
    void shouldReturnNotFoundWhenEvaluationDoesNotExist() {

        EvaluationsCliCommand command =
            command(
                emptyListPort(),
                evaluationId ->
                    Optional.empty()
            );

        TestConsole console =
            new TestConsole();

        CliExitCode result =
            command.execute(
                List.of(
                    "show",
                    "999"
                ),
                console.out(),
                console.err()
            );

        assertEquals(
            CliExitCode.NOT_FOUND,
            result
        );

        assertTrue(
            console.stderr()
                .contains(
                    "Evaluation not found: 999"
                )
        );
    }

    @Test
    void shouldRejectInvalidShowEvaluationId() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "show",
                    "invalid"
                ),
                console.out(),
                console.err()
            )
        );

        assertThrows(
            CliUsageException.class,
            () -> command.execute(
                List.of(
                    "show",
                    "0"
                ),
                console.out(),
                console.err()
            )
        );
    }

    @Test
    void shouldRejectAdditionalShowArguments() {

        EvaluationsCliCommand command =
            emptyCommand();

        TestConsole console =
            new TestConsole();

        CliUsageException exception =
            assertThrows(
                CliUsageException.class,
                () -> command.execute(
                    List.of(
                        "show",
                        "123",
                        "extra"
                    ),
                    console.out(),
                    console.err()
                )
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    "exactly one evaluation-id"
                )
        );
    }

    private DealEvaluationSummary summary() {

        return new DealEvaluationSummary(
            123L,
            456L,
            789L,
            new Asin(
                "B0CLI14002"
            ),
            "Produto\tcom\nquebra",
            Money.of(
                "129.90"
            ),
            true,
            null,
            new BigDecimal(
                "84.2500"
            ),
            new BigDecimal(
                "0.7500"
            ),
            COLLECTED_AT,
            EVALUATED_AT
        );
    }

    private DealEvaluationDetail detailedEvaluation() {

        OperationalEvaluationRuleResult ratingRule =
            new OperationalEvaluationRuleResult(
                1,
                "MIN_RATING",
                true,
                "4.8",
                "4.3",
                null
            );

        OperationalEvaluationRuleResult reviewsRule =
            new OperationalEvaluationRuleResult(
                2,
                "MIN_REVIEW_COUNT",
                true,
                "1500",
                "100",
                null
            );

        OperationalScoreFactorResult discountFactor =
            new OperationalScoreFactorResult(
                1,
                "DISCOUNT",
                "APPLIED",
                new BigDecimal(
                    "25.00"
                ),
                new BigDecimal(
                    "0.7500"
                ),
                new BigDecimal(
                    "0.4000"
                ),
                new BigDecimal(
                    "30.0000"
                )
            );

        OperationalScoreFactorResult ratingFactor =
            new OperationalScoreFactorResult(
                2,
                "RATING",
                "APPLIED",
                new BigDecimal(
                    "4.8"
                ),
                new BigDecimal(
                    "0.9000"
                ),
                new BigDecimal(
                    "0.3000"
                ),
                new BigDecimal(
                    "27.0000"
                )
            );

        OperationalMomentumAudit momentumAudit =
            new OperationalMomentumAudit(
                9001L,
                "MOMENTUM_V1",
                "AVAILABLE",
                null,
                455L,
                3600L,
                new BigDecimal(
                    "5.00"
                ),
                new BigDecimal(
                    "-10.00"
                ),
                new BigDecimal(
                    "-7.1500"
                ),
                new BigDecimal(
                    "2.00"
                ),
                new BigDecimal(
                    "0.7500"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T20:05:01-03:00"
                )
            );

        return new DealEvaluationDetail(
            summary(),
            "https://example.invalid/product",
            Money.of(
                "159.90"
            ),
            Money.of(
                "169.90"
            ),
            new BigDecimal(
                "75"
            ),
            4.8,
            1500L,
            "Amazon",
            "Amazon",
            "amazon-deals",
            "ELIGIBILITY_V1",
            "COMMERCIAL_FILTER_V1",
            "SCORE_V1",
            List.of(
                ratingRule,
                reviewsRule
            ),
            List.of(
                discountFactor,
                ratingFactor
            ),
            "MOMENTUM_V1",
            momentumAudit
        );
    }

    private EvaluationsCliCommand emptyCommand() {

        return command(
            emptyListPort(),
            evaluationId ->
                Optional.empty()
        );
    }

    private DealEvaluationOperationalQueryPort emptyListPort() {

        return criteria ->
            new DealEvaluationPage(
                List.of(),
                null
            );
    }

    private EvaluationsCliCommand command(
        DealEvaluationOperationalQueryPort listPort
    ) {

        return command(
            listPort,
            evaluationId ->
                Optional.empty()
        );
    }

    private EvaluationsCliCommand command(
        DealEvaluationOperationalQueryPort listPort,
        DealEvaluationOperationalDetailQueryPort detailPort
    ) {

        return new EvaluationsCliCommand(
            new ListDealEvaluationsUseCase(
                listPort
            ),
            new GetDealEvaluationDetailUseCase(
                detailPort
            )
        );
    }

    private static final class TestConsole {

        private final ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        private final ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        private final PrintWriter out =
            new PrintWriter(
                stdout,
                true,
                StandardCharsets.UTF_8
            );

        private final PrintWriter err =
            new PrintWriter(
                stderr,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter out() {
            return out;
        }

        PrintWriter err() {
            return err;
        }

        String stdout() {

            out.flush();

            return normalizeLineEndings(
                stdout.toString(
                    StandardCharsets.UTF_8
                )
            );
        }

        String stderr() {

            err.flush();

            return normalizeLineEndings(
                stderr.toString(
                    StandardCharsets.UTF_8
                )
            );
        }

        private String normalizeLineEndings(
            String value
        ) {

            return value
                .replace(
                    "\r\n",
                    "\n"
                )
                .replace(
                    "\r",
                    "\n"
                );
        }
    }
}

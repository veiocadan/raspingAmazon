package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationsCliIntegrationTest {

    @Test
    void shouldDispatchEvaluationsListThroughOperationalCli() {

        TestFixture fixture =
            createFixture(
                evaluationId ->
                    Optional.empty()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "evaluations",
                        "list",
                        "--limit",
                        "10"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .startsWith(
                    "EVALUATION_ID\t"
                )
        );

        assertTrue(
            fixture.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldTranslateInvalidEvaluationArgumentsToUsageExitCode() {

        TestFixture fixture =
            createFixture(
                evaluationId ->
                    Optional.empty()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "evaluations",
                        "list",
                        "--limit",
                        "invalid"
                    }
                );

        assertEquals(
            CliExitCode.USAGE_ERROR,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "--limit must be a positive integer"
                )
        );
    }

    @Test
    void shouldDispatchEvaluationShowThroughOperationalCli() {

        DealEvaluationSummary summary =
            new DealEvaluationSummary(
                123L,
                456L,
                789L,
                new Asin(
                    "B0CLI14006"
                ),
                "Produto integrado",
                Money.of(
                    "99.90"
                ),
                true,
                null,
                new BigDecimal(
                    "80.0000"
                ),
                null,
                OffsetDateTime.parse(
                    "2026-09-24T20:00:00-03:00"
                ),
                OffsetDateTime.parse(
                    "2026-09-24T20:05:00-03:00"
                )
            );

        DealEvaluationDetail detail =
            new DealEvaluationDetail(
                summary,
                "https://example.invalid/integrated",
                null,
                null,
                null,
                null,
                null,
                "Amazon",
                "Amazon",
                "amazon-deals",
                "ELIGIBILITY_V1",
                "COMMERCIAL_FILTER_V1",
                "SCORE_V1",
                List.of(),
                List.of(),
                null,
                null
            );

        TestFixture fixture =
            createFixture(
                evaluationId ->
                    Optional.of(
                        detail
                    )
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "evaluations",
                        "show",
                        "123"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "EVALUATION_ID\t123"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "ASIN\tB0CLI14006"
                )
        );

        assertTrue(
            fixture.stdout()
                .contains(
                    "RULE_RESULTS"
                )
        );

        assertTrue(
            fixture.stderr()
                .isEmpty()
        );
    }

    @Test
    void shouldPreserveEvaluationNotFoundExitCode() {

        TestFixture fixture =
            createFixture(
                evaluationId ->
                    Optional.empty()
            );

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "evaluations",
                        "show",
                        "999"
                    }
                );

        assertEquals(
            CliExitCode.NOT_FOUND,
            result
        );

        assertTrue(
            fixture.stderr()
                .contains(
                    "Evaluation not found: 999"
                )
        );
    }

    private TestFixture createFixture(
        com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalDetailQueryPort detailPort
    ) {

        ListDealEvaluationsUseCase listUseCase =
            new ListDealEvaluationsUseCase(
                criteria ->
                    new DealEvaluationPage(
                        List.of(),
                        null
                    )
            );

        GetDealEvaluationDetailUseCase detailUseCase =
            new GetDealEvaluationDetailUseCase(
                detailPort
            );

        EvaluationsCliCommand evaluations =
            new EvaluationsCliCommand(
                listUseCase,
                detailUseCase
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        OperationalCli cli =
            new OperationalCli(
                Map.of(
                    "evaluations",
                    evaluations
                ),
                new PrintWriter(
                    stdout,
                    true,
                    StandardCharsets.UTF_8
                ),
                new PrintWriter(
                    stderr,
                    true,
                    StandardCharsets.UTF_8
                )
            );

        return new TestFixture(
            cli,
            stdout,
            stderr
        );
    }

    private record TestFixture(
        OperationalCli cli,
        ByteArrayOutputStream stdoutBuffer,
        ByteArrayOutputStream stderrBuffer
    ) {

        String stdout() {

            return stdoutBuffer.toString(
                StandardCharsets.UTF_8
            );
        }

        String stderr() {

            return stderrBuffer.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}

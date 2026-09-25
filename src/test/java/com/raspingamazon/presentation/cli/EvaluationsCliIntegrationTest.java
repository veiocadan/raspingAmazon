package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationsCliIntegrationTest {

    @Test
    void shouldDispatchEvaluationsListThroughOperationalCli() {

        ListDealEvaluationsUseCase useCase =
            new ListDealEvaluationsUseCase(
                criteria ->
                    new DealEvaluationPage(
                        List.of(),
                        null
                    )
            );

        EvaluationsCliCommand evaluations =
            new EvaluationsCliCommand(
                useCase
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

        CliExitCode result =
            cli.run(
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
            stdout.toString(
                    StandardCharsets.UTF_8
                )
                .startsWith(
                    "EVALUATION_ID\t"
                )
        );

        assertTrue(
            stderr.toString(
                    StandardCharsets.UTF_8
                )
                .isEmpty()
        );
    }

    @Test
    void shouldTranslateInvalidEvaluationArgumentsToUsageExitCode() {

        ListDealEvaluationsUseCase useCase =
            new ListDealEvaluationsUseCase(
                criteria ->
                    new DealEvaluationPage(
                        List.of(),
                        null
                    )
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        OperationalCli cli =
            new OperationalCli(
                Map.of(
                    "evaluations",
                    new EvaluationsCliCommand(
                        useCase
                    )
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

        CliExitCode result =
            cli.run(
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
            stderr.toString(
                    StandardCharsets.UTF_8
                )
                .contains(
                    "--limit must be a positive integer"
                )
        );
    }
}

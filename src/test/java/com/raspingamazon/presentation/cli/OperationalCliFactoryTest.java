package com.raspingamazon.presentation.cli;

import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.GetDealEvaluationDetailUseCase;
import com.raspingamazon.application.operation.evaluation.ListDealEvaluationsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ListProcessingJobsUseCase;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.run.ListProcessingRunsUseCase;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.publication.GetPublicationDetailUseCase;
import com.raspingamazon.application.operation.publication.ListPublicationsUseCase;
import com.raspingamazon.application.operation.publication.PublicationPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalCliFactoryTest {

    @Test
    void shouldWireEvaluationsCommand() {

        TestFixture fixture =
            createFixture();

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "evaluations",
                        "list"
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
    }

    @Test
    void shouldWireRunsCommand() {

        TestFixture fixture =
            createFixture();

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "runs",
                        "list"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .startsWith(
                    "RUN_ID\t"
                )
        );
    }

    @Test
    void shouldWireJobsCommand() {

        TestFixture fixture =
            createFixture();

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "jobs",
                        "list"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .startsWith(
                    "JOB_ID\t"
                )
        );
    }

    @Test
    void shouldWirePublicationsCommand() {

        TestFixture fixture =
            createFixture();

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "publications",
                        "list"
                    }
                );

        assertEquals(
            CliExitCode.SUCCESS,
            result
        );

        assertTrue(
            fixture.stdout()
                .startsWith(
                    "PUBLICATION_ID\t"
                )
        );
    }

    @Test
    void shouldWireEvaluationDetailUseCase() {

        TestFixture fixture =
            createFixture();

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

    @Test
    void shouldWirePublicationDetailUseCase() {

        TestFixture fixture =
            createFixture();

        CliExitCode result =
            fixture.cli()
                .run(
                    new String[]{
                        "publications",
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
                    "Publication not found: 999"
                )
        );
    }

    @Test
    void shouldRejectNullRequiredDependency() {

        TestFixture fixture =
            createFixture();

        assertThrows(
            NullPointerException.class,
            () -> OperationalCliFactory.create(
                null,
                fixture.getDealEvaluationDetailUseCase(),
                fixture.listProcessingRunsUseCase(),
                fixture.listProcessingJobsUseCase(),
                fixture.listPublicationsUseCase(),
                fixture.getPublicationDetailUseCase(),
                fixture.out(),
                fixture.err()
            )
        );
    }

    private TestFixture createFixture() {

        ListDealEvaluationsUseCase
            listDealEvaluationsUseCase =
            new ListDealEvaluationsUseCase(
                criteria ->
                    new DealEvaluationPage(
                        List.of(),
                        null
                    )
            );

        GetDealEvaluationDetailUseCase
            getDealEvaluationDetailUseCase =
            new GetDealEvaluationDetailUseCase(
                evaluationId ->
                    Optional.empty()
            );

        ListProcessingRunsUseCase
            listProcessingRunsUseCase =
            new ListProcessingRunsUseCase(
                criteria ->
                    new ProcessingRunPage(
                        List.of(),
                        null
                    )
            );

        ListProcessingJobsUseCase
            listProcessingJobsUseCase =
            new ListProcessingJobsUseCase(
                criteria ->
                    new ProcessingJobPage(
                        List.of(),
                        null
                    )
            );

        ListPublicationsUseCase
            listPublicationsUseCase =
            new ListPublicationsUseCase(
                criteria ->
                    new PublicationPage(
                        List.of(),
                        null
                    )
            );

        GetPublicationDetailUseCase
            getPublicationDetailUseCase =
            new GetPublicationDetailUseCase(
                publicationId ->
                    Optional.empty()
            );

        ByteArrayOutputStream stdout =
            new ByteArrayOutputStream();

        ByteArrayOutputStream stderr =
            new ByteArrayOutputStream();

        PrintWriter out =
            new PrintWriter(
                stdout,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter err =
            new PrintWriter(
                stderr,
                true,
                StandardCharsets.UTF_8
            );

        OperationalCli cli =
            OperationalCliFactory.create(
                listDealEvaluationsUseCase,
                getDealEvaluationDetailUseCase,
                listProcessingRunsUseCase,
                listProcessingJobsUseCase,
                listPublicationsUseCase,
                getPublicationDetailUseCase,
                out,
                err
            );

        return new TestFixture(
            cli,
            listDealEvaluationsUseCase,
            getDealEvaluationDetailUseCase,
            listProcessingRunsUseCase,
            listProcessingJobsUseCase,
            listPublicationsUseCase,
            getPublicationDetailUseCase,
            out,
            err,
            stdout,
            stderr
        );
    }

    private record TestFixture(
        OperationalCli cli,
        ListDealEvaluationsUseCase listDealEvaluationsUseCase,
        GetDealEvaluationDetailUseCase getDealEvaluationDetailUseCase,
        ListProcessingRunsUseCase listProcessingRunsUseCase,
        ListProcessingJobsUseCase listProcessingJobsUseCase,
        ListPublicationsUseCase listPublicationsUseCase,
        GetPublicationDetailUseCase getPublicationDetailUseCase,
        PrintWriter out,
        PrintWriter err,
        ByteArrayOutputStream stdoutBuffer,
        ByteArrayOutputStream stderrBuffer
    ) {

        String stdout() {

            out.flush();

            return stdoutBuffer.toString(
                StandardCharsets.UTF_8
            );
        }

        String stderr() {

            err.flush();

            return stderrBuffer.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}

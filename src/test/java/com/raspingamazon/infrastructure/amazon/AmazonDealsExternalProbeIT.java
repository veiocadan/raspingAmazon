package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AmazonDealsExternalProbeIT {

    private static final Path DIAGNOSTIC_DIRECTORY =
        Path.of(
            "target",
            "diagnostics"
        );

    private static final int MAX_ATTEMPTS =
        3;

    private static final Duration FIRST_RETRY_DELAY =
        Duration.ofSeconds(
            2
        );

    private static final Duration SECOND_RETRY_DELAY =
        Duration.ofSeconds(
            5
        );

    private final DefaultProcessingFailureClassifier
        failureClassifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldCollectRealAmazonDealsSource()
        throws Exception {

        AmazonDealsCollector collector =
            AmazonDealsCollectorFactory.create();

        CollectionResult result =
            null;

        for (int attempt = 1;
             attempt <= MAX_ATTEMPTS;
             attempt++) {

            try {

                result =
                    collector.collect();

                break;

            } catch (CollectionException exception) {

                FailureClassification classification =
                    failureClassifier.classify(
                        exception
                    );

                if (!classification.retryable()) {

                    writeFailureDiagnostic(
                        exception,
                        classification,
                        attempt,
                        "FAILURE"
                    );

                    printFailure(
                        exception,
                        classification,
                        attempt
                    );

                    throw exception;
                }

                if (attempt >= MAX_ATTEMPTS) {

                    writeFailureDiagnostic(
                        exception,
                        classification,
                        attempt,
                        "INCONCLUSIVE"
                    );

                    printInconclusive(
                        exception,
                        classification,
                        attempt
                    );

                    /*
                     * Uma falha transitória persistente não comprova
                     * quebra do contrato da fonte.
                     *
                     * A probe é marcada como abortada/inconclusiva,
                     * preservando o diagnóstico sem transformar
                     * indisponibilidade externa em regressão do projeto.
                     */
                    assumeTrue(
                        false,
                        "Amazon source probe inconclusive after "
                            + MAX_ATTEMPTS
                            + " transient attempts: "
                            + classification.code()
                    );

                    return;
                }

                Duration retryDelay =
                    retryDelayForAttempt(
                        attempt
                    );

                printRetry(
                    exception,
                    classification,
                    attempt,
                    retryDelay
                );

                sleepBeforeRetry(
                    retryDelay
                );
            }
        }

        assertNotNull(
            result
        );

        assertNotNull(
            result.source()
        );

        assertFalse(
            result.source()
                .isBlank()
        );

        assertNotNull(
            result.collectedAt()
        );

        assertNotNull(
            result.content()
        );

        assertFalse(
            result.content()
                .isBlank()
        );

        Files.createDirectories(
            DIAGNOSTIC_DIRECTORY
        );

        Path diagnosticFile =
            DIAGNOSTIC_DIRECTORY.resolve(
                "amazon-deals-real.html"
            );

        Files.writeString(
            diagnosticFile,
            result.content(),
            StandardCharsets.UTF_8
        );

        assertTrue(
            Files.exists(
                diagnosticFile
            )
        );

        assertTrue(
            Files.size(
                diagnosticFile
            ) > 0
        );

        writeSuccessDiagnostic(
            result
        );

        printSuccess(
            result,
            diagnosticFile
        );
    }

    private Duration retryDelayForAttempt(
        int completedAttempt
    ) {

        if (completedAttempt == 1) {
            return FIRST_RETRY_DELAY;
        }

        return SECOND_RETRY_DELAY;
    }

    private void sleepBeforeRetry(
        Duration delay
    ) throws InterruptedException {

        Thread.sleep(
            delay.toMillis()
        );
    }

    private void writeSuccessDiagnostic(
        CollectionResult result
    ) throws Exception {

        Files.createDirectories(
            DIAGNOSTIC_DIRECTORY
        );

        Path diagnosticFile =
            DIAGNOSTIC_DIRECTORY.resolve(
                "amazon-source-probe-status.txt"
            );

        String diagnostic =
            """
            Amazon Deals external probe

            Status: SUCCESS
            Timestamp: %s
            Source: %s
            Collected at: %s
            Content length: %s
            Attempts: <= %s
            """.formatted(
                OffsetDateTime.now(),
                result.source(),
                result.collectedAt(),
                result.content().length(),
                MAX_ATTEMPTS
            );

        Files.writeString(
            diagnosticFile,
            diagnostic,
            StandardCharsets.UTF_8
        );
    }

    private void writeFailureDiagnostic(
        CollectionException exception,
        FailureClassification classification,
        int attempt,
        String probeStatus
    ) throws Exception {

        Files.createDirectories(
            DIAGNOSTIC_DIRECTORY
        );

        Path diagnosticFile =
            DIAGNOSTIC_DIRECTORY.resolve(
                "amazon-source-probe-failure.txt"
            );

        String bodyExcerpt =
            exception.responseBodyExcerpt();

        if (bodyExcerpt == null
            || bodyExcerpt.isBlank()) {

            bodyExcerpt =
                "<empty>";
        }

        String diagnostic =
            """
            Amazon Deals external probe

            Status: %s
            Timestamp: %s
            Attempt: %s
            Maximum attempts: %s

            Failure type: %s
            Failure code: %s

            HTTP status: %s
            Message: %s

            Response body excerpt:
            %s
            """.formatted(
                probeStatus,
                OffsetDateTime.now(),
                attempt,
                MAX_ATTEMPTS,
                classification.type(),
                classification.code(),
                exception.httpStatusCode(),
                exception.getMessage(),
                bodyExcerpt
            );

        Files.writeString(
            diagnosticFile,
            diagnostic,
            StandardCharsets.UTF_8
        );
    }

    private void printRetry(
        CollectionException exception,
        FailureClassification classification,
        int attempt,
        Duration delay
    ) {

        System.out.println();

        System.out.println(
            "=== AMAZON DEALS EXTERNAL PROBE ==="
        );

        System.out.println(
            "Status: RETRY"
        );

        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );

        System.out.println(
            "Failure type: "
                + classification.type()
        );

        System.out.println(
            "Failure code: "
                + classification.code()
        );

        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );

        System.out.println(
            "Retry in: "
                + delay.toSeconds()
                + " seconds"
        );

        System.out.println(
            "==================================="
        );

        System.out.println();
    }

    private void printFailure(
        CollectionException exception,
        FailureClassification classification,
        int attempt
    ) {

        System.out.println();

        System.out.println(
            "=== AMAZON DEALS EXTERNAL PROBE ==="
        );

        System.out.println(
            "Status: FAILURE"
        );

        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );

        System.out.println(
            "Failure type: "
                + classification.type()
        );

        System.out.println(
            "Failure code: "
                + classification.code()
        );

        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );

        System.out.println(
            "Diagnostic artifact: "
                + DIAGNOSTIC_DIRECTORY
                .resolve(
                    "amazon-source-probe-failure.txt"
                )
                .toAbsolutePath()
        );

        System.out.println(
            "==================================="
        );

        System.out.println();
    }

    private void printInconclusive(
        CollectionException exception,
        FailureClassification classification,
        int attempt
    ) {

        System.out.println();

        System.out.println(
            "=== AMAZON DEALS EXTERNAL PROBE ==="
        );

        System.out.println(
            "Status: INCONCLUSIVE"
        );

        System.out.println(
            "Attempt: "
                + attempt
                + "/"
                + MAX_ATTEMPTS
        );

        System.out.println(
            "Failure type: "
                + classification.type()
        );

        System.out.println(
            "Failure code: "
                + classification.code()
        );

        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
        );

        System.out.println(
            "Diagnostic artifact: "
                + DIAGNOSTIC_DIRECTORY
                .resolve(
                    "amazon-source-probe-failure.txt"
                )
                .toAbsolutePath()
        );

        System.out.println(
            "==================================="
        );

        System.out.println();
    }

    private void printSuccess(
        CollectionResult result,
        Path diagnosticFile
    ) {

        System.out.println();

        System.out.println(
            "=== AMAZON DEALS EXTERNAL PROBE ==="
        );

        System.out.println(
            "Status: SUCCESS"
        );

        System.out.println(
            "Source: "
                + result.source()
        );

        System.out.println(
            "Collected at: "
                + result.collectedAt()
        );

        System.out.println(
            "Content length: "
                + result.content()
                .length()
        );

        System.out.println(
            "Diagnostic artifact: "
                + diagnosticFile.toAbsolutePath()
        );

        System.out.println(
            "==================================="
        );

        System.out.println();
    }
}

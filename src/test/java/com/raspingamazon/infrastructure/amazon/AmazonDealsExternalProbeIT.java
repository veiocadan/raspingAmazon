package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonDealsExternalProbeIT {

    private static final Path DIAGNOSTIC_DIRECTORY =
        Path.of(
            "target",
            "diagnostics"
        );

    @Test
    void shouldCollectRealAmazonDealsSource()
        throws Exception {

        AmazonDealsCollector collector =
            AmazonDealsCollectorFactory.create();

        CollectionResult result;

        try {

            result =
                collector.collect();

        } catch (CollectionException exception) {

            writeFailureDiagnostic(
                exception
            );

            /*
             * Importante:
             *
             * A probe continua sendo uma verificação real.
             * Não transformamos 503, 429 ou qualquer outra falha
             * externa em sucesso.
             */
            throw exception;
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

    private void writeFailureDiagnostic(
        CollectionException exception
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
            Amazon Deals external probe failure

            Timestamp: %s
            HTTP status: %s
            Message: %s

            Response body excerpt:
            %s
            """.formatted(
                OffsetDateTime.now(),
                exception.httpStatusCode(),
                exception.getMessage(),
                bodyExcerpt
            );

        Files.writeString(
            diagnosticFile,
            diagnostic,
            StandardCharsets.UTF_8
        );

        System.out.println();
        System.out.println(
            "=== AMAZON DEALS EXTERNAL PROBE ==="
        );

        System.out.println(
            "Status: FAILURE"
        );

        System.out.println(
            "HTTP status: "
                + exception.httpStatusCode()
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

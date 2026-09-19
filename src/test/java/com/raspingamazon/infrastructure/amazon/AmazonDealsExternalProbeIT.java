package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probe externa da fonte Amazon Deals.
 *
 * <p>Esta classe NÃO pertence à suíte hermética padrão.</p>
 *
 * <p>O sufixo {@code IT} é intencional: o Maven Surefire não descobre
 * esta classe durante um {@code mvn test} comum. Ela é habilitada
 * explicitamente pelo profile Maven {@code external-probe}.</p>
 *
 * <p>Diferentemente do antigo AmazonDealsRealCollectionTest, esta
 * probe não transforma falha da fonte em sucesso. Se coleta, contrato
 * ou conteúdo falharem, o teste também falha.</p>
 */
class AmazonDealsExternalProbeIT {

    @Test
    void shouldCollectRealAmazonDealsSource()
            throws Exception {

        /*
         * Utilizamos exatamente a composição real da coleta.
         *
         * Não existe mock, fixture ou servidor local nesta execução.
         */
        AmazonDealsCollector collector =
                AmazonDealsCollectorFactory.create();

        /*
         * Qualquer CollectionException propagará naturalmente e fará
         * a probe terminar com falha.
         */
        CollectionResult result =
                collector.collect();

        /*
         * Além de HTTP bem-sucedido, exigimos um resultado estrutural
         * mínimo válido.
         */
        assertNotNull(
                result
        );

        assertNotNull(
                result.source()
        );

        assertFalse(
                result.source().isBlank()
        );

        assertNotNull(
                result.collectedAt()
        );

        assertNotNull(
                result.content()
        );

        assertFalse(
                result.content().isBlank()
        );

        /*
         * O conteúdo bruto é preservado somente como artefato
         * diagnóstico em target/.
         *
         * Ele não passa a fazer parte do domínio nem da persistência.
         */
        Path diagnosticDirectory =
                Path.of(
                        "target",
                        "diagnostics"
                );

        Files.createDirectories(
                diagnosticDirectory
        );

        Path diagnosticFile =
                diagnosticDirectory.resolve(
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

        /*
         * A saída contém somente metadados úteis.
         *
         * Não despejamos o HTML no terminal.
         */
        System.out.println();
        System.out.println(
                "=== AMAZON DEALS EXTERNAL PROBE ==="
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
                        + result.content().length()
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
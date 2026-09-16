package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Probe manual para executar uma coleta real da página de promoções
 * da Amazon Brasil.
 *
 * <p>Esta classe não é um teste JUnit e, propositalmente, seu nome
 * não segue o padrão de descoberta do Maven Surefire. Dessa forma,
 * uma execução normal de {@code mvn test} permanece determinística
 * e não depende da disponibilidade da Amazon.</p>
 *
 * <p>A probe utiliza exatamente a composição funcional da FASE 5:
 * {@link AmazonDealsCollectorFactory} cria o transporte HTTP, o
 * coletor HTTP genérico e o adaptador específico da Amazon.</p>
 *
 * <p>Quando a fonte responde com sucesso, o conteúdo bruto é salvo
 * em {@code target/diagnostics/amazon-deals-real.html}. O artefato
 * existe apenas como evidência diagnóstica da coleta real e não é
 * utilizado por nenhuma etapa posterior do sistema.</p>
 */
public final class AmazonDealsCollectorRealSourceProbe {

    /**
     * Construtor privado porque esta classe possui somente uma operação
     * de execução manual.
     */
    private AmazonDealsCollectorRealSourceProbe() {
        // Impede instanciação acidental da probe.
    }

    /**
     * Executa uma coleta real da fonte de promoções.
     *
     * @param args argumentos de linha de comando; atualmente não utilizados
     */
    public static void main(String[] args) {

        System.out.println(
                "Iniciando coleta real da fonte Amazon Deals..."
        );

        try {
            /*
             * A composição utilizada aqui é a mesma que será utilizada
             * pela aplicação. Nenhum mock ou servidor local participa
             * desta execução.
             */
            AmazonDealsCollector collector =
                    AmazonDealsCollectorFactory.create();

            CollectionResult result = collector.collect();

            /*
             * A coleta foi considerada bem-sucedida pelo contrato.
             * Registramos apenas metadados no console e persistimos
             * o conteúdo bruto como artefato diagnóstico.
             */
            System.out.println(
                    "Coleta realizada com sucesso."
            );

            System.out.println(
                    "Fonte: " + result.source()
            );

            System.out.println(
                    "Instante da coleta: " + result.collectedAt()
            );

            System.out.println(
                    "Tamanho do conteúdo: "
                            + result.content().length()
                            + " caracteres"
            );

            Path diagnosticDirectory =
                    Path.of("target", "diagnostics");

            Files.createDirectories(diagnosticDirectory);

            Path diagnosticFile =
                    diagnosticDirectory.resolve(
                            "amazon-deals-real.html"
                    );

            Files.writeString(
                    diagnosticFile,
                    result.content(),
                    StandardCharsets.UTF_8
            );

            System.out.println(
                    "Artefato diagnóstico salvo em: "
                            + diagnosticFile.toAbsolutePath()
            );

        } catch (CollectionException exception) {

            /*
             * Falhas da coleta são exibidas explicitamente. A probe
             * não transforma uma indisponibilidade da fonte em sucesso.
             */
            System.err.println(
                    "A coleta real falhou."
            );

            System.err.println(
                    "Mensagem: " + exception.getMessage()
            );

            if (exception.getCause() != null) {
                System.err.println(
                        "Causa: "
                                + exception.getCause().getClass().getName()
                                + ": "
                                + exception.getCause().getMessage()
                );
            }

            /*
             * A exceção é relançada para que a execução manual termine
             * com código de erro e a falha fique claramente registrada.
             */
            throw exception;

        } catch (Exception exception) {

            /*
             * Falhas inesperadas também devem resultar em execução
             * malsucedida, sem serem silenciosamente ignoradas.
             */
            System.err.println(
                    "Falha inesperada durante a probe."
            );

            exception.printStackTrace();

            throw new IllegalStateException(
                    "Real Amazon deals source probe failed",
                    exception
            );
        }
    }
}
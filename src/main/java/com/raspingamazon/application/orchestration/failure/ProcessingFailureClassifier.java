package com.raspingamazon.application.orchestration.failure;

/**
 * Classifica uma exceção de execução em uma falha normalizada.
 *
 * <p>O worker depende desta abstração e não precisa conhecer detalhes
 * de HTTP, JDBC ou adapters concretos.</p>
 */
@FunctionalInterface
public interface ProcessingFailureClassifier {

    FailureClassification classify(
        Throwable failure
    );
}

package com.raspingamazon.infrastructure.amazon;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionResult;
import org.junit.jupiter.api.Test;

/**
 * Teste de diagnóstico da coleta contra a fonte real de promoções da Amazon.
 *
 * <p>Este teste pertence à FASE 5 e tem como objetivo verificar somente
 * a capacidade de coleta da fonte real. Ele não interpreta o conteúdo,
 * não extrai ASIN, não normaliza preços e não aplica regras de negócio.</p>
 *
 * <p>Como a fonte externa pode estar indisponível, protegida ou sofrer
 * alterações, uma falha de acesso é registrada no resultado do teste
 * sem transformar essa situação em uma exceção não tratada.</p>
 */
class AmazonDealsRealCollectionTest {

    /**
     * Executa uma coleta real da página de promoções e registra
     * informações diagnósticas sobre o resultado obtido.
     */
    @Test
    void shouldCollectRealAmazonDealsSource() {

        AmazonDealsCollector collector =
                AmazonDealsCollectorFactory.create();

        try {
            CollectionResult result = collector.collect();

            System.out.println();
            System.out.println("=== AMAZON DEALS REAL COLLECTION ===");
            System.out.println("Source: " + result.source());
            System.out.println("Collected at: " + result.collectedAt());
            System.out.println("Content length: " + result.content().length());
            System.out.println("=====================================");
            System.out.println();

        } catch (CollectionException exception) {

            System.out.println();
            System.out.println("=== AMAZON DEALS COLLECTION FAILURE ===");
            System.out.println("Failure type: "
                    + exception.getClass().getSimpleName());
            System.out.println("Message: " + exception.getMessage());

            if (exception.getCause() != null) {
                System.out.println("Cause: "
                        + exception.getCause().getClass().getSimpleName());
                System.out.println("Cause message: "
                        + exception.getCause().getMessage());
            }

            System.out.println("=======================================");
            System.out.println();
        }
    }
}
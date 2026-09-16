package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes para respostas HTTP sem conteúdo.
 *
 * <p>Uma resposta HTTP pode possuir um status de sucesso e, ainda assim,
 * não fornecer conteúdo útil para a etapa de coleta. O contrato
 * {@code CollectionResult} já estabelece que o conteúdo coletado não
 * pode ser nulo ou vazio.</p>
 *
 * <p>Este teste verifica que essa regra do contrato seja respeitada
 * quando uma resposta HTTP bem-sucedida possui corpo vazio.</p>
 *
 * <p>Nenhuma interpretação do conteúdo é realizada nesta etapa.</p>
 */
class HttpCollectionCollectorEmptyResponseTest {

    /**
     * URI fictícia utilizada para a coleta controlada.
     */
    private static final URI SOURCE =
            URI.create("https://example.com/deals");

    /**
     * Relógio fixo para manter a execução determinística.
     */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    Instant.parse("2026-09-16T22:00:00Z"),
                    ZoneOffset.UTC
            );

    /**
     * Verifica que uma resposta HTTP 200 com corpo vazio não seja
     * transformada em um resultado de coleta válido.
     *
     * <p>A validação do conteúdo vazio permanece no contrato
     * {@code CollectionResult}. O collector não duplica essa regra.</p>
     */
    @Test
    void shouldRejectSuccessfulResponseWithEmptyContent() {

        HttpTransport transport = uri -> {
            assertEquals(SOURCE, uri);

            return new HttpTransportResponse(
                    200,
                    ""
            );
        };

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        CollectionException exception = assertThrows(
                CollectionException.class,
                () -> collector.collect(request)
        );

        /*
         * A mensagem abaixo vem da falha de criação do CollectionResult,
         * que é encapsulada pelo HttpCollectionCollector como uma falha
         * operacional de coleta.
         */
        assertEquals(
                "Collection failed",
                exception.getMessage()
        );

        /*
         * A causa original deve permanecer disponível para diagnóstico.
         */
        assertEquals(
                IllegalArgumentException.class,
                exception.getCause().getClass()
        );
    }

    /**
     * Verifica que conteúdo composto somente por espaços também seja
     * rejeitado pelo contrato de resultado.
     */
    @Test
    void shouldRejectSuccessfulResponseWithBlankContent() {

        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        200,
                        "   "
                );

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        CollectionException exception = assertThrows(
                CollectionException.class,
                () -> collector.collect(request)
        );

        assertEquals(
                "Collection failed",
                exception.getMessage()
        );

        assertEquals(
                IllegalArgumentException.class,
                exception.getCause().getClass()
        );
    }

    /**
     * Confirma que conteúdo não vazio continua sendo aceito normalmente.
     */
    @Test
    void shouldAcceptNonBlankContent() {

        HttpTransport transport = uri ->
                new HttpTransportResponse(
                        200,
                        "valid-content"
                );

        var collector = new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
        );

        var request = new CollectionRequest(SOURCE);

        var result = collector.collect(request);

        assertEquals(
                "valid-content",
                result.content()
        );

        assertEquals(
                "https://example.com/deals",
                result.source()
        );

        assertEquals(
                Instant.parse("2026-09-16T22:00:00Z"),
                result.collectedAt().toInstant()
        );
    }
}
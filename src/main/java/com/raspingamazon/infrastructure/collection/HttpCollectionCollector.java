package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Implementação do coletor baseado em HTTP.
 *
 * <p>Esta classe adapta o transporte HTTP ao contrato de coleta
 * definido pela aplicação.</p>
 *
 * <p>A classe permanece deliberadamente genérica: não conhece Amazon,
 * HTML, JSON, ASIN, ofertas ou regras de negócio.</p>
 */
public final class HttpCollectionCollector
    implements CollectionCollector {

    private static final int MAX_ERROR_BODY_EXCERPT_LENGTH =
        2000;

    private final HttpTransport httpTransport;

    private final Clock clock;

    public HttpCollectionCollector(
        HttpTransport httpTransport,
        Clock clock
    ) {
        if (httpTransport == null) {
            throw new NullPointerException(
                "HTTP transport must not be null"
            );
        }

        if (clock == null) {
            throw new NullPointerException(
                "Clock must not be null"
            );
        }

        this.httpTransport =
            httpTransport;

        this.clock =
            clock;
    }

    @Override
    public CollectionResult collect(
        CollectionRequest request
    ) {
        if (request == null) {
            throw new NullPointerException(
                "Collection request must not be null"
            );
        }

        try {
            HttpTransportResponse response =
                httpTransport.get(
                    request.source()
                );

            if (!isSuccessful(
                response.statusCode()
            )) {

                throw new CollectionException(
                    "HTTP response status indicates collection failure: "
                        + response.statusCode(),
                    response.statusCode(),
                    createBodyExcerpt(
                        response.body()
                    )
                );
            }

            return new CollectionResult(
                response.body(),
                OffsetDateTime.now(
                    clock
                ),
                request.source().toString()
            );

        } catch (CollectionException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new CollectionException(
                "Collection failed",
                exception
            );
        }
    }

    private boolean isSuccessful(
        int statusCode
    ) {
        return statusCode >= 200
            && statusCode < 300;
    }

    private String createBodyExcerpt(
        String body
    ) {
        if (body == null
            || body.isBlank()) {

            return null;
        }

        if (body.length()
            <= MAX_ERROR_BODY_EXCERPT_LENGTH) {

            return body;
        }

        return body.substring(
            0,
            MAX_ERROR_BODY_EXCERPT_LENGTH
        );
    }
}

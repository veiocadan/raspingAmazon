package com.raspingamazon.application.operation.publication;

import com.raspingamazon.application.operation.publication.port.PublicationOperationalQueryPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListPublicationsUseCaseTest {

    @Test
    void shouldDelegateSearchToQueryPort() {

        PublicationSearchCriteria criteria =
            PublicationSearchCriteria.firstPage();

        PublicationPage expected =
            new PublicationPage(
                List.of(),
                null
            );

        AtomicReference<PublicationSearchCriteria> received =
            new AtomicReference<>();

        PublicationOperationalQueryPort queryPort =
            value -> {

                received.set(
                    value
                );

                return expected;
            };

        ListPublicationsUseCase useCase =
            new ListPublicationsUseCase(
                queryPort
            );

        PublicationPage actual =
            useCase.execute(
                criteria
            );

        assertSame(
            criteria,
            received.get()
        );

        assertSame(
            expected,
            actual
        );
    }

    @Test
    void shouldRejectNullCriteria() {

        ListPublicationsUseCase useCase =
            new ListPublicationsUseCase(
                criteria -> new PublicationPage(
                    List.of(),
                    null
                )
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                null
            )
        );
    }

    @Test
    void shouldRejectNullQueryPort() {

        assertThrows(
            NullPointerException.class,
            () -> new ListPublicationsUseCase(
                null
            )
        );
    }

    @Test
    void shouldRejectNullPageReturnedByPort() {

        ListPublicationsUseCase useCase =
            new ListPublicationsUseCase(
                criteria -> null
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                PublicationSearchCriteria.firstPage()
            )
        );
    }
}

package com.raspingamazon.application.operation.publication;

import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.PublicationStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOperationalContractsTest {

    private static final OffsetDateTime FIRST_TIME =
        OffsetDateTime.parse(
            "2026-09-24T10:00:00-03:00"
        );

    private static final OffsetDateTime SECOND_TIME =
        OffsetDateTime.parse(
            "2026-09-24T11:00:00-03:00"
        );

    @Test
    void shouldCreateValidCursor() {

        PublicationCursor cursor =
            new PublicationCursor(
                SECOND_TIME,
                20L
            );

        assertEquals(
            SECOND_TIME,
            cursor.createdAt()
        );

        assertEquals(
            20L,
            cursor.publicationId()
        );
    }

    @Test
    void shouldRejectInvalidCursorId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationCursor(
                SECOND_TIME,
                0L
            )
        );
    }

    @Test
    void shouldCreateDefaultSearchCriteria() {

        PublicationSearchCriteria criteria =
            PublicationSearchCriteria.firstPage();

        assertNull(
            criteria.status()
        );

        assertNull(
            criteria.asin()
        );

        assertNull(
            criteria.after()
        );

        assertEquals(
            PublicationSearchCriteria.DEFAULT_LIMIT,
            criteria.limit()
        );
    }

    @Test
    void shouldRejectInvalidLimits() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                PublicationSearchCriteria.MAX_LIMIT + 1
            )
        );
    }

    @Test
    void shouldRejectInvalidEvaluationId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationSearchCriteria(
                null,
                null,
                0L,
                null,
                null,
                null,
                50
            )
        );
    }

    @Test
    void shouldRejectInvertedCreationRange() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationSearchCriteria(
                null,
                null,
                null,
                SECOND_TIME,
                FIRST_TIME,
                null,
                50
            )
        );
    }

    @Test
    void shouldCreatePublicationSummary() {

        PublicationSummary summary =
            summary(
                20L,
                SECOND_TIME,
                PublicationStatus.CREATED.name()
            );

        assertEquals(
            20L,
            summary.publicationId()
        );

        assertEquals(
            PublicationStatus.CREATED.name(),
            summary.status()
        );

        assertEquals(
            "B0PUB14001",
            summary.asin()
                .value()
        );
    }

    @Test
    void shouldCreateOrderedPage() {

        PublicationSummary newest =
            summary(
                20L,
                SECOND_TIME,
                PublicationStatus.CREATED.name()
            );

        PublicationSummary oldest =
            summary(
                10L,
                FIRST_TIME,
                PublicationStatus.PUBLISHED.name()
            );

        PublicationCursor cursor =
            new PublicationCursor(
                oldest.createdAt(),
                oldest.publicationId()
            );

        PublicationPage page =
            new PublicationPage(
                List.of(
                    newest,
                    oldest
                ),
                cursor
            );

        assertEquals(
            2,
            page.items()
                .size()
        );

        assertTrue(
            page.hasNextPage()
        );

        assertEquals(
            cursor,
            page.nextCursor()
        );
    }

    @Test
    void shouldRejectUnorderedPage() {

        PublicationSummary older =
            summary(
                10L,
                FIRST_TIME,
                PublicationStatus.CREATED.name()
            );

        PublicationSummary newer =
            summary(
                20L,
                SECOND_TIME,
                PublicationStatus.CREATED.name()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationPage(
                List.of(
                    older,
                    newer
                ),
                null
            )
        );
    }

    @Test
    void shouldDefensivelyCopyPageItems() {

        ArrayList<PublicationSummary> mutable =
            new ArrayList<>();

        mutable.add(
            summary(
                20L,
                SECOND_TIME,
                PublicationStatus.CREATED.name()
            )
        );

        PublicationPage page =
            new PublicationPage(
                mutable,
                null
            );

        mutable.clear();

        assertEquals(
            1,
            page.items()
                .size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> page.items()
                .clear()
        );

        assertFalse(
            page.hasNextPage()
        );
    }

    private PublicationSummary summary(
        long publicationId,
        OffsetDateTime createdAt,
        String status
    ) {

        return new PublicationSummary(
            publicationId,
            publicationId + 100L,
            publicationId + 200L,
            new Asin(
                "B0PUB14001"
            ),
            "Produto publicação",
            status,
            "TEMPLATE_V1",
            "COMMERCIAL_PRESENTATION_V1",
            "AFFILIATE_LINK_V1",
            createdAt
        );
    }
}

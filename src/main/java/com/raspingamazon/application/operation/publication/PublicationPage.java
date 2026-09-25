package com.raspingamazon.application.operation.publication;

import java.util.List;
import java.util.Objects;

/**
 * Página operacional de Publication.
 */
public record PublicationPage(
    List<PublicationSummary> items,
    PublicationCursor nextCursor
) {

    public PublicationPage {

        Objects.requireNonNull(
            items,
            "PublicationPage items must not be null"
        );

        items =
            List.copyOf(
                items
            );

        validateOrdering(
            items
        );

        if (items.isEmpty()
            && nextCursor != null) {

            throw new IllegalArgumentException(
                "Empty PublicationPage must not have nextCursor"
            );
        }

        if (nextCursor != null) {

            PublicationSummary last =
                items.get(
                    items.size() - 1
                );

            PublicationCursor expected =
                new PublicationCursor(
                    last.createdAt(),
                    last.publicationId()
                );

            if (!expected.equals(
                nextCursor
            )) {

                throw new IllegalArgumentException(
                    "PublicationPage nextCursor "
                        + "must represent the last page item"
                );
            }
        }
    }

    public boolean hasNextPage() {
        return nextCursor != null;
    }

    private static void validateOrdering(
        List<PublicationSummary> items
    ) {

        for (int index = 1;
             index < items.size();
             index++) {

            PublicationSummary previous =
                items.get(
                    index - 1
                );

            PublicationSummary current =
                items.get(
                    index
                );

            if (previous.createdAt()
                .isBefore(
                    current.createdAt()
                )) {

                throw new IllegalArgumentException(
                    "PublicationPage items must be ordered "
                        + "by createdAt DESC and publicationId DESC"
                );
            }

            if (previous.createdAt()
                .isEqual(
                    current.createdAt()
                )
                && previous.publicationId()
                <= current.publicationId()) {

                throw new IllegalArgumentException(
                    "PublicationPage items must be ordered "
                        + "by createdAt DESC and publicationId DESC"
                );
            }
        }
    }
}

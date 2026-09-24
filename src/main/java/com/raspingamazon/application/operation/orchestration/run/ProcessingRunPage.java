package com.raspingamazon.application.operation.orchestration.run;

import java.util.List;
import java.util.Objects;

/**
 * Página operacional de ProcessingRun.
 */
public record ProcessingRunPage(
    List<ProcessingRunSummary> items,
    ProcessingRunCursor nextCursor
) {

    public ProcessingRunPage {

        Objects.requireNonNull(
            items,
            "ProcessingRunPage items must not be null"
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
                "Empty ProcessingRunPage must not have nextCursor"
            );
        }

        if (nextCursor != null) {

            ProcessingRunSummary last =
                items.get(
                    items.size() - 1
                );

            ProcessingRunCursor expected =
                new ProcessingRunCursor(
                    last.requestedAt(),
                    last.runId()
                );

            if (!expected.equals(
                nextCursor
            )) {

                throw new IllegalArgumentException(
                    "ProcessingRunPage nextCursor "
                        + "must represent the last page item"
                );
            }
        }
    }

    public boolean hasNextPage() {
        return nextCursor != null;
    }

    private static void validateOrdering(
        List<ProcessingRunSummary> items
    ) {

        for (int index = 1;
             index < items.size();
             index++) {

            ProcessingRunSummary previous =
                items.get(
                    index - 1
                );

            ProcessingRunSummary current =
                items.get(
                    index
                );

            if (previous.requestedAt()
                .isBefore(
                    current.requestedAt()
                )) {

                throw new IllegalArgumentException(
                    "ProcessingRunPage items must be ordered "
                        + "by requestedAt DESC and runId DESC"
                );
            }

            if (previous.requestedAt()
                .isEqual(
                    current.requestedAt()
                )
                && previous.runId()
                <= current.runId()) {

                throw new IllegalArgumentException(
                    "ProcessingRunPage items must be ordered "
                        + "by requestedAt DESC and runId DESC"
                );
            }
        }
    }
}

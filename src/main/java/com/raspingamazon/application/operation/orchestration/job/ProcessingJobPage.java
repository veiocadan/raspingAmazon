package com.raspingamazon.application.operation.orchestration.job;

import java.util.List;
import java.util.Objects;

/**
 * Página operacional de ProcessingJob.
 */
public record ProcessingJobPage(
    List<ProcessingJobSummary> items,
    ProcessingJobCursor nextCursor
) {

    public ProcessingJobPage {

        Objects.requireNonNull(
            items,
            "ProcessingJobPage items must not be null"
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
                "Empty ProcessingJobPage must not have nextCursor"
            );
        }

        if (nextCursor != null) {

            ProcessingJobSummary last =
                items.get(
                    items.size() - 1
                );

            ProcessingJobCursor expected =
                new ProcessingJobCursor(
                    last.createdAt(),
                    last.jobId()
                );

            if (!expected.equals(
                nextCursor
            )) {

                throw new IllegalArgumentException(
                    "ProcessingJobPage nextCursor "
                        + "must represent the last page item"
                );
            }
        }
    }

    public boolean hasNextPage() {
        return nextCursor != null;
    }

    private static void validateOrdering(
        List<ProcessingJobSummary> items
    ) {

        for (int index = 1;
             index < items.size();
             index++) {

            ProcessingJobSummary previous =
                items.get(
                    index - 1
                );

            ProcessingJobSummary current =
                items.get(
                    index
                );

            if (previous.createdAt()
                .isBefore(
                    current.createdAt()
                )) {

                throw new IllegalArgumentException(
                    "ProcessingJobPage items must be ordered "
                        + "by createdAt DESC and jobId DESC"
                );
            }

            if (previous.createdAt()
                .isEqual(
                    current.createdAt()
                )
                && previous.jobId()
                <= current.jobId()) {

                throw new IllegalArgumentException(
                    "ProcessingJobPage items must be ordered "
                        + "by createdAt DESC and jobId DESC"
                );
            }
        }
    }
}

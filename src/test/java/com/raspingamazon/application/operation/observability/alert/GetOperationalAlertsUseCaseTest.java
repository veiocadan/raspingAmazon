package com.raspingamazon.application.operation.observability.alert;

import com.raspingamazon.application.operation.observability.alert.port.OperationalAlertQueryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetOperationalAlertsUseCaseTest {

    private static final Instant EVALUATED_INSTANT =
        Instant.parse(
            "2026-09-26T18:00:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            EVALUATED_INSTANT,
            ZoneOffset.UTC
        );

    @Test
    void shouldQueryAlertsUsingExplicitPolicyAndClockInstant() {

        OperationalAlertPolicy policy =
            policy();

        AtomicReference<OperationalAlertPolicy>
            receivedPolicy =
            new AtomicReference<>();

        AtomicReference<OffsetDateTime>
            receivedEvaluatedAt =
            new AtomicReference<>();

        OperationalAlert expected =
            new OperationalAlert(
                OperationalAlertType.DEAD_JOBS,
                100L,
                null,
                2L,
                null
            );

        OperationalAlertQueryPort queryPort =
            (queryPolicy, evaluatedAt) -> {

                receivedPolicy.set(
                    queryPolicy
                );

                receivedEvaluatedAt.set(
                    evaluatedAt
                );

                return List.of(
                    expected
                );
            };

        GetOperationalAlertsUseCase useCase =
            new GetOperationalAlertsUseCase(
                queryPort,
                policy,
                FIXED_CLOCK
            );

        List<OperationalAlert> result =
            useCase.execute();

        assertSame(
            policy,
            receivedPolicy.get()
        );

        assertEquals(
            OffsetDateTime.parse(
                "2026-09-26T18:00:00Z"
            ),
            receivedEvaluatedAt.get()
        );

        assertEquals(
            List.of(
                expected
            ),
            result
        );
    }

    @Test
    void shouldReturnImmutableSnapshot() {

        List<OperationalAlert> mutable =
            new ArrayList<>();

        mutable.add(
            new OperationalAlert(
                OperationalAlertType.ZERO_CANDIDATES,
                200L,
                null,
                0L,
                null
            )
        );

        OperationalAlertQueryPort queryPort =
            (policy, evaluatedAt) ->
                mutable;

        GetOperationalAlertsUseCase useCase =
            new GetOperationalAlertsUseCase(
                queryPort,
                policy(),
                FIXED_CLOCK
            );

        List<OperationalAlert> result =
            useCase.execute();

        mutable.clear();

        assertEquals(
            1,
            result.size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> result.add(
                new OperationalAlert(
                    OperationalAlertType.DEAD_JOBS,
                    300L,
                    null,
                    1L,
                    null
                )
            )
        );
    }

    @Test
    void shouldRejectInvalidDependenciesAndNullQueryResult() {

        OperationalAlertQueryPort queryPort =
            (policy, evaluatedAt) ->
                List.of();

        OperationalAlertPolicy policy =
            policy();

        assertThrows(
            NullPointerException.class,
            () -> new GetOperationalAlertsUseCase(
                null,
                policy,
                FIXED_CLOCK
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new GetOperationalAlertsUseCase(
                queryPort,
                null,
                FIXED_CLOCK
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new GetOperationalAlertsUseCase(
                queryPort,
                policy,
                null
            )
        );

        OperationalAlertQueryPort invalidQueryPort =
            (queryPolicy, evaluatedAt) ->
                null;

        GetOperationalAlertsUseCase useCase =
            new GetOperationalAlertsUseCase(
                invalidQueryPort,
                policy,
                FIXED_CLOCK
            );

        assertThrows(
            NullPointerException.class,
            useCase::execute
        );
    }

    private OperationalAlertPolicy policy() {

        return new OperationalAlertPolicy(
            3,
            Duration.ofMinutes(
                15
            ),
            5,
            new BigDecimal(
                "0.50"
            ),
            20L
        );
    }
}

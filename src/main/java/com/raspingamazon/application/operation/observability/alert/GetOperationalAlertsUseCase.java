package com.raspingamazon.application.operation.observability.alert;

import com.raspingamazon.application.operation.observability.alert.port.OperationalAlertQueryPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso de leitura dos alertas operacionais.
 *
 * <p>Uma chamada representa uma única avaliação explícita do estado
 * persistido.</p>
 *
 * <p>Não existe scheduler, loop, sleep ou polling nesta classe.</p>
 */
public final class GetOperationalAlertsUseCase {

    private final OperationalAlertQueryPort
        queryPort;

    private final OperationalAlertPolicy
        policy;

    private final Clock
        clock;

    public GetOperationalAlertsUseCase(
        OperationalAlertQueryPort queryPort,
        OperationalAlertPolicy policy,
        Clock clock
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "queryPort must not be null"
            );

        this.policy =
            Objects.requireNonNull(
                policy,
                "policy must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Avalia os alertas uma única vez.
     */
    public List<OperationalAlert> execute() {

        OffsetDateTime evaluatedAt =
            OffsetDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
            );

        List<OperationalAlert> alerts =
            Objects.requireNonNull(
                queryPort.findActiveAlerts(
                    policy,
                    evaluatedAt
                ),
                "OperationalAlertQueryPort must not return null"
            );

        return List.copyOf(
            alerts
        );
    }
}

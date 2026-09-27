package com.raspingamazon.application.operation.observability.alert;

import com.raspingamazon.application.operation.observability.alert.port.OperationalAlertPolicyProvider;
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
 * <p>A política é obtida somente no momento da execução. Isso evita
 * tornar configuração de alertas um pré-requisito para outros
 * comandos da interface operacional.</p>
 *
 * <p>Não existe scheduler, loop, sleep ou polling nesta classe.</p>
 */
public final class GetOperationalAlertsUseCase {

    private final OperationalAlertQueryPort
        queryPort;

    private final OperationalAlertPolicyProvider
        policyProvider;

    private final Clock
        clock;

    /**
     * Cria o caso de uso.
     *
     * @param queryPort porta de consulta dos fatos operacionais
     * @param policyProvider fonte explícita da política de alertas
     * @param clock relógio da avaliação
     */
    public GetOperationalAlertsUseCase(
        OperationalAlertQueryPort queryPort,
        OperationalAlertPolicyProvider policyProvider,
        Clock clock
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "queryPort must not be null"
            );

        this.policyProvider =
            Objects.requireNonNull(
                policyProvider,
                "policyProvider must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Avalia os alertas uma única vez.
     *
     * <p>A política é carregada nesta chamada, e não durante a criação
     * do composition root. Portanto comandos operacionais que não
     * consultam alertas não dependem da configuração dos seus
     * limiares.</p>
     */
    public List<OperationalAlert> execute() {

        OperationalAlertPolicy policy =
            Objects.requireNonNull(
                policyProvider.load(),
                "OperationalAlertPolicyProvider must not return null"
            );

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

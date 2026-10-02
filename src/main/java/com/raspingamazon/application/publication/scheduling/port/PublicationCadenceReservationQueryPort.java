package com.raspingamazon.application.publication.scheduling.port;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Consulta o histórico persistente de reservas que efetivamente
 * ocupam quota e participam da cadência primária de publicação.
 *
 * <p>Esta porta não conta quota e não calcula novos horários.</p>
 *
 * <p>Sua responsabilidade é somente responder qual foi o
 * {@code availableAt} mais distante já reservado para:</p>
 *
 * <pre>
 * channel
 * +
 * destination
 * +
 * quotaDate
 * </pre>
 *
 * <p>Entregas derivadas que não reservam quota não fazem parte
 * deste histórico.</p>
 */
@FunctionalInterface
public interface PublicationCadenceReservationQueryPort {

    Optional<OffsetDateTime> findLastReservedAvailableAt(
        String channel,
        String destination,
        LocalDate quotaDate
    );
}

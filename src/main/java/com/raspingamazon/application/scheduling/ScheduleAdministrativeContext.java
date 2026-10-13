package com.raspingamazon.application.scheduling;

import java.util.Objects;

/**
 * Metadados declarados de uma alteracao administrativa de agendamento.
 * O ator da CLI e informativo; nao substitui autenticacao.
 */
public record ScheduleAdministrativeContext(String actor, String reason) {

    public ScheduleAdministrativeContext {
        actor = requireSafeText(actor, "actor", 120);
        reason = requireSafeText(reason, "reason", 300);
    }

    public static ScheduleAdministrativeContext useCaseReference(String operation) {
        return new ScheduleAdministrativeContext(
            "application-usecase", "use-case:schedules-" + requireSafeText(operation, "operation", 64)
        );
    }

    private static String requireSafeText(String value, String name, int max) {
        Objects.requireNonNull(value, name + " must not be null");
        String text = value.trim();
        if (text.isEmpty() || text.length() > max || text.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(name + " must be printable and within the size limit");
        }
        return text;
    }
}

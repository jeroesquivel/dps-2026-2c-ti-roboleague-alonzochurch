package com.dps.roboleague.adapter.in.web.round;

import java.util.List;

/**
 * Contrato de entrada de la programación de una ronda. {@code start} es una fecha y hora ISO local
 * sin zona (por ejemplo {@code 2026-03-02T10:00:00}): se interpreta como la hora local de la
 * competencia y nunca se le aplica la zona horaria del servidor.
 */
public record ScheduleRoundRequest(String competitionId, String categoryId, String challengeId, Integer ordinal,
        List<HeatRequest> heats, String actor) {

    public record HeatRequest(String teamId, String arenaId, String start, Integer durationMinutes,
            List<String> judges) {
    }
}

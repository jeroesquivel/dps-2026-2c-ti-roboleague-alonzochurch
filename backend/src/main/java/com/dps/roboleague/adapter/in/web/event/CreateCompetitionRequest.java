package com.dps.roboleague.adapter.in.web.event;

import java.util.List;

/**
 * Contrato de entrada de la creación de una competencia con su reglamento inicial. Los campos son
 * opcionales a nivel de formato: la presencia y las invariantes se verifican al traducirlos a tipos
 * del núcleo, que responde con un error de entrada en lugar de una falla interna.
 */
public record CreateCompetitionRequest(String name, PeriodDto period, List<CategoryRequest> categories,
        RulebookRequest rulebook, String actor) {

    public record CategoryRequest(String name, Integer minimumAge, Integer maximumAge, String robotClass) {
    }
}

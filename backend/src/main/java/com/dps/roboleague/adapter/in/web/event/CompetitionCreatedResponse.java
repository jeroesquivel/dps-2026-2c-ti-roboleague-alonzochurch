package com.dps.roboleague.adapter.in.web.event;

import java.util.List;

/**
 * Resultado de crear una competencia. {@code categories} conserva el orden de la solicitud: la
 * categoría {@code i} de la respuesta es la {@code i} de la entrada, y repite su nombre para que la
 * correspondencia sea evidente.
 */
public record CompetitionCreatedResponse(String competitionId, String rulebookVersion,
        List<CategoryCreated> categories) {

    public record CategoryCreated(String categoryId, String name) {
    }
}

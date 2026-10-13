package com.dps.roboleague.adapter.in.web.event;

import java.util.List;

/** Vista pública de una competencia: datos, categorías y versión activa del reglamento. */
public record CompetitionResponse(String competitionId, String name, PeriodDto period, String rulebookVersion,
        List<CategoryResponse> categories) {

    public record CategoryResponse(String categoryId, String name, int minimumAge, int maximumAge,
            String robotClass) {
    }
}

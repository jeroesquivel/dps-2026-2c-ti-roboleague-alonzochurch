package com.dps.roboleague.adapter.in.web.standings;

import java.util.List;

/** Todas las revisiones de una categoría, de la primera a la última. */
public record RevisionsResponse(List<StandingsResponse> revisions) {
}

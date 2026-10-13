package com.dps.roboleague.adapter.in.web.standings;

/** Cuerpo de las operaciones que sólo necesitan identificar a quien las ejecuta (generar y publicar). */
public record StandingsActionRequest(String actor) {
}

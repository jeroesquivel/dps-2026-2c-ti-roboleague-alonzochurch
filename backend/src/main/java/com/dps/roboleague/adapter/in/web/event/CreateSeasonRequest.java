package com.dps.roboleague.adapter.in.web.event;

/** Contrato de entrada de la creación de una temporada. */
public record CreateSeasonRequest(String name, Integer year, PeriodDto period, String actor) {
}

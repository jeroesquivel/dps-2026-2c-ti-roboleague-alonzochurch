package com.dps.roboleague.adapter.in.web.run;

/** Incidente observado: el código de la penalidad del desafío y cuántas veces ocurrió. */
public record IncidentRequest(String code, Integer occurrences) {
}

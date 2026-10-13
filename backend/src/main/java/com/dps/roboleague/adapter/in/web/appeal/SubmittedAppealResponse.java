package com.dps.roboleague.adapter.in.web.appeal;

/** Identificador de la apelación presentada; el detalle se consulta en {@code GET /appeals/{appealId}}. */
public record SubmittedAppealResponse(String appealId) {
}

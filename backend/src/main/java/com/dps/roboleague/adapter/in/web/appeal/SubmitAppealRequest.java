package com.dps.roboleague.adapter.in.web.appeal;

/** Contrato de entrada para presentar una apelación sobre una corrida. */
public record SubmitAppealRequest(String runId, String teamId, String claim, String actor) {
}

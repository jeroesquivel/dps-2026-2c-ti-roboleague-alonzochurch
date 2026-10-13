package com.dps.roboleague.adapter.in.web.appeal;

/**
 * Estado de una apelación. {@code status} es {@code SUBMITTED}, {@code ACCEPTED} o {@code REJECTED};
 * {@code decision} es {@code null} mientras la apelación está pendiente.
 */
public record AppealResponse(String appealId, String runId, String teamId, String claim, String submittedAt,
        String status, DecisionResponse decision) {

    public record DecisionResponse(String reviewer, String rationale, String decidedAt) {
    }
}

package com.dps.roboleague.adapter.in.web.appeal;

/** Contrato de entrada para rechazar una apelación con fundamento. */
public record RejectAppealRequest(String rationale, String reviewer) {
}

package com.dps.roboleague.adapter.in.web.audit;

import java.util.List;

/** Traza de un sujeto, en orden temporal. {@code events} es una lista vacía si el sujeto no tiene eventos. */
public record AuditTrailResponse(String subjectId, List<AuditEventResponse> events) {
}

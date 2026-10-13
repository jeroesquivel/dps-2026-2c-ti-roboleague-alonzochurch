package com.dps.roboleague.adapter.in.web.audit;

import java.util.Map;

/**
 * Un evento de la traza tal como lo ve el cliente. Los nombres de acción y de detalle son los del
 * contrato público y los decide {@link AuditWebMapper}; no son los nombres internos del dominio.
 */
public record AuditEventResponse(String occurredAt, String action, String subjectId, String actor,
        Map<String, String> details) {
}

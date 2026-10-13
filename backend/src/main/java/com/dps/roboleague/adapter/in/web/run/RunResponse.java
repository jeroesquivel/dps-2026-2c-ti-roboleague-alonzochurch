package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Detalle de una corrida: el resultado vigente, el original capturado, las correcciones aplicadas y
 * el estado de completitud. Las evaluaciones del panel son las recibidas, ya que una corrección no las
 * modifica. {@code status} es {@code CAPTURED} o {@code CORRECTED}. Las mediciones
 * se indican por clave de métrica y los incidentes por código de penalidad.
 */
public record RunResponse(String runId, String roundId, String heatId, String teamId, String challengeId,
        String rulebookVersion, int attemptNumber, String capturedAt, String status,
        Map<String, BigDecimal> currentMeasurements, List<IncidentResponse> currentIncidents,
        List<EvaluationResponse> evaluations,
        Map<String, BigDecimal> originalMeasurements, List<IncidentResponse> originalIncidents,
        List<CorrectionResponse> corrections, CompletionResponse completion) {

    public record IncidentResponse(String code, int occurrences) {
    }

    /** Evaluación de un juez para un criterio; es la que recibió la corrida, no se corrige. */
    public record EvaluationResponse(String judgeId, String criterion, BigDecimal score) {
    }

    public record CorrectionResponse(String appliedAt, String actor, String reason,
            Map<String, BigDecimal> measurements, List<IncidentResponse> incidents, String sourceAppeal) {
    }

    /** {@code status} es {@code PENDING} o {@code COMPLETE}; las fuentes son {@code AUTOMATIC} o {@code JUDGES}. */
    public record CompletionResponse(String status, List<String> expectedSources, List<String> missingSources,
            List<ReceiptResponse> receipts) {
    }

    public record ReceiptResponse(String source, String actor, String receivedAt) {
    }
}

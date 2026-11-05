package com.dps.roboleague.adapter.in.web.run;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Detalle de una corrida: el resultado vigente, el original capturado, las correcciones aplicadas y
 * el estado de completitud. {@code evaluations} son las vigentes y {@code originalEvaluations} las que
 * recibió la corrida; difieren sólo si alguna corrección las reemplazó. {@code status} es {@code CAPTURED} o {@code CORRECTED}. Las mediciones
 * se indican por clave de métrica y los incidentes por código de penalidad.
 */
public record RunResponse(String runId, String roundId, String heatId, String teamId, String challengeId,
        String rulebookVersion, int attemptNumber, String capturedAt, String status,
        Map<String, BigDecimal> currentMeasurements, List<IncidentResponse> currentIncidents,
        List<EvaluationResponse> evaluations, List<EvaluationResponse> originalEvaluations,
        Map<String, BigDecimal> originalMeasurements, List<IncidentResponse> originalIncidents,
        List<CorrectionResponse> corrections, CompletionResponse completion) {

    public record IncidentResponse(String code, int occurrences) {
    }

    /** Evaluación de un juez para un criterio. */
    public record EvaluationResponse(String judgeId, String criterion, BigDecimal score) {
    }

    public record CorrectionResponse(String appliedAt, String actor, String reason,
            Map<String, BigDecimal> measurements, List<IncidentResponse> incidents,
            List<EvaluationResponse> evaluations, OriginResponse origin) {
    }

    /** Por qué se corrigió: {@code kind} es {@code APPEAL} o {@code DATA_ENTRY_ERROR}. */
    public record OriginResponse(String kind, String description) {
    }

    /** {@code status} es {@code PENDING} o {@code COMPLETE}; las fuentes son {@code AUTOMATIC} o {@code JUDGES}. */
    public record CompletionResponse(String status, List<String> expectedSources, List<String> missingSources,
            List<ReceiptResponse> receipts) {
    }

    public record ReceiptResponse(String source, String actor, String receivedAt) {
    }
}

package com.dps.roboleague.adapter.in.web;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.SourceReceipt;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluation;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.JudgeScore;
import com.dps.roboleague.domain.scoring.PenaltyCode;
import com.dps.roboleague.domain.shared.JudgeId;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class RunResultWebMapper {

    private RunResultWebMapper() {
    }

    static MeasurementSet measurements(Map<String, BigDecimal> values) {
        MeasurementSet measurements = MeasurementSet.empty();
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            measurements = measurements.with(MetricKey.of(entry.getKey()), MetricValue.of(entry.getValue()));
        }
        return measurements;
    }

    static JudgeEvaluations evaluations(List<RunResultController.EvaluationRequest> values) {
        return new JudgeEvaluations(values.stream()
                .map(value -> new JudgeEvaluation(JudgeId.of(value.judgeId()), MetricKey.of(value.criterion()),
                        new JudgeScore(value.score())))
                .toList());
    }

    static List<IncidentReport> incidents(List<RunResultController.IncidentRequest> values) {
        return values.stream()
                .map(value -> new IncidentReport(PenaltyCode.of(value.code()), value.occurrences()))
                .toList();
    }

    static RunResultController.RunResponse response(RunResult run) {
        Map<String, BigDecimal> measurements = new TreeMap<>();
        run.currentMeasurements().values().forEach((key, value) -> measurements.put(key.value(), value.amount()));
        List<RunResultController.EvaluationResponse> evaluations = run.evaluations().evaluations().stream()
                .map(value -> new RunResultController.EvaluationResponse(value.judge().value(),
                        value.criterion().value(), value.score().value()))
                .toList();
        List<RunResultController.IncidentResponse> incidents = run.currentIncidents().stream()
                .map(value -> new RunResultController.IncidentResponse(value.code().value(), value.occurrences()))
                .toList();
        Map<ResultSource, SourceReceipt> received = new EnumMap<>(ResultSource.class);
        run.completion().receipts().forEach(receipt -> received.put(receipt.source(), receipt));
        List<RunResultController.SourceResponse> sources = List.of(ResultSource.values()).stream()
                .map(source -> source(source, received.get(source)))
                .toList();
        return new RunResultController.RunResponse(run.id().value(), run.roundId().value(), run.heatId().value(),
                run.teamId().value(), run.challengeId().value(), run.attemptNumber().value(),
                run.rulebookVersion().number(), run.capturedAt(), run.completion().status(),
                run.completion().missing(), sources, measurements, evaluations, incidents, run.status(),
                run.corrections().entries().size());
    }

    private static RunResultController.SourceResponse source(ResultSource source, SourceReceipt receipt) {
        return receipt == null
                ? new RunResultController.SourceResponse(source, false, null, null)
                : new RunResultController.SourceResponse(source, true, receipt.actor().name(), receipt.receivedAt());
    }
}

package com.dps.roboleague.adapter.in.web.run;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.SourceBreakdown;
import com.dps.roboleague.domain.challenge.SourcedScore;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.result.ResultCorrection;
import com.dps.roboleague.domain.result.RunCompletion;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.SourceReceipt;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Proyecta a DTOs web lo que el núcleo ya decidió; no calcula ni corrige nada. */
final class RunWebMapper {

    private RunWebMapper() {
    }

    static RunResponse toResponse(RunResult run) {
        return new RunResponse(run.id().value(), run.roundId().value(), run.heatId().value(), run.teamId().value(),
                run.challengeId().value(), run.rulebookVersion().toString(), run.attemptNumber().value(),
                run.capturedAt().toString(), run.status().name(), measurementsOf(run.currentMeasurements()),
                incidentsOf(run.currentIncidents()), measurementsOf(run.originalMeasurements()),
                incidentsOf(run.originalIncidents()),
                run.corrections().entries().stream().map(RunWebMapper::correctionOf).toList(),
                completionOf(run.completion()));
    }

    static RunScoreResponse toResponse(CalculateRunScore.RunScore score) {
        return new RunScoreResponse(score.runId().value(), score.teamId().value(), score.challengeId().value(),
                score.rulebookVersion().toString(), score.total().value(), contributionsOf(score.breakdown()),
                score.completion().status().name(), score.bySource().map(RunWebMapper::bySourceOf).orElse(null));
    }

    private static Map<String, BigDecimal> measurementsOf(MeasurementSet measurements) {
        Map<String, BigDecimal> sorted = new TreeMap<>();
        measurements.values().forEach((key, value) -> sorted.put(key.value(), value.amount()));
        return sorted;
    }

    private static List<RunResponse.IncidentResponse> incidentsOf(List<IncidentReport> incidents) {
        return incidents.stream()
                .map(incident -> new RunResponse.IncidentResponse(incident.code().value(), incident.occurrences()))
                .toList();
    }

    private static RunResponse.CorrectionResponse correctionOf(ResultCorrection correction) {
        return new RunResponse.CorrectionResponse(correction.appliedAt().toString(), correction.actor().name(),
                correction.reason(), measurementsOf(correction.measurements()), incidentsOf(correction.incidents()),
                correction.sourceAppeal().value());
    }

    private static RunResponse.CompletionResponse completionOf(RunCompletion completion) {
        return new RunResponse.CompletionResponse(completion.status().name(),
                completion.expected().stream().map(Enum::name).sorted().toList(),
                completion.missing().stream().map(Enum::name).toList(),
                completion.receipts().stream().map(RunWebMapper::receiptOf).toList());
    }

    private static RunResponse.ReceiptResponse receiptOf(SourceReceipt receipt) {
        return new RunResponse.ReceiptResponse(receipt.source().name(), receipt.actor().name(),
                receipt.receivedAt().toString());
    }

    private static List<RunScoreResponse.ContributionResponse> contributionsOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream().map(RunWebMapper::contributionOf).toList();
    }

    private static RunScoreResponse.ContributionResponse contributionOf(ScoreContribution contribution) {
        return new RunScoreResponse.ContributionResponse(contribution.ruleCode().value(), contribution.kind().name(),
                contribution.explanation(), contribution.points().value());
    }

    private static RunScoreResponse.BySourceResponse bySourceOf(SourcedScore sourced) {
        return new RunScoreResponse.BySourceResponse(sourced.sources().stream().map(RunWebMapper::sourceOf).toList(),
                new RunScoreResponse.GroupResponse(sourced.acrossSources().total().value(),
                        contributionsOf(sourced.acrossSources())));
    }

    private static RunScoreResponse.SourceResponse sourceOf(SourceBreakdown source) {
        return new RunScoreResponse.SourceResponse(source.source().name(), source.subtotal().value(),
                contributionsOf(source.breakdown()));
    }
}

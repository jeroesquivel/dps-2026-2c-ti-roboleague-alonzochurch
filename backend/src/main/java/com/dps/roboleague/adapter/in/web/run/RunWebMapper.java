package com.dps.roboleague.adapter.in.web.run;

import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.port.in.CorrectResult;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.results.result.ResultCorrection;
import com.dps.roboleague.results.result.RunCompletion;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.SourceReceipt;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.challenge.MeasurementSet;
import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.challenge.MetricValue;
import com.dps.roboleague.rules.challenge.SourceBreakdown;
import com.dps.roboleague.rules.challenge.SourcedScore;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.PenaltyCode;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Proyecta a DTOs web lo que el núcleo ya decidió; no calcula ni corrige nada. */
final class RunWebMapper {

    private RunWebMapper() {
    }

    // ---- solicitudes: sólo presencia y forma; las reglas del desafío las decide el núcleo ----------------

    static CaptureRunResult.Command toCommand(CaptureRunRequest request) {
        return new CaptureRunResult.Command(roundOf(request.roundId()), teamOf(request.teamId()),
                attemptOf(request.attemptNumber()), measurementsOf(request.measurements()),
                evaluationsOf(request.evaluations()), incidentsOf(request.incidents()), actorOf(request.actor()));
    }

    static RegisterAutomaticMeasurements.Command toCommand(AutomaticMeasurementsRequest request) {
        return new RegisterAutomaticMeasurements.Command(roundOf(request.roundId()), teamOf(request.teamId()),
                attemptOf(request.attemptNumber()), measurementsOf(request.measurements()),
                actorOf(request.actor()));
    }

    static RegisterPanelEvaluations.Command toCommand(PanelEvaluationsRequest request) {
        return new RegisterPanelEvaluations.Command(roundOf(request.roundId()), teamOf(request.teamId()),
                attemptOf(request.attemptNumber()), evaluationsOf(request.evaluations()),
                incidentsOf(request.incidents()), actorOf(request.actor()));
    }

    static CorrectResult.Command toCommand(String runId, CorrectResultRequest request) {
        ResultAmendment amendment = ResultAmendment.of(measurementsOf(request.measurements()),
                incidentsOf(request.incidents()));
        return new CorrectResult.Command(RunId.of(runId), required(request.reason(), "reason"),
                request.evaluations() == null ? amendment : amendment.replacingEvaluations(
                        evaluationsOf(request.evaluations())),
                actorOf(request.actor()));
    }

    private static RoundId roundOf(String id) {
        return RoundId.of(required(id, "roundId"));
    }

    private static TeamId teamOf(String id) {
        return TeamId.of(required(id, "teamId"));
    }

    private static Actor actorOf(String name) {
        return Actor.of(required(name, "actor"));
    }

    private static AttemptNumber attemptOf(Integer attemptNumber) {
        return AttemptNumber.of(required(attemptNumber, "attemptNumber"));
    }

    private static MeasurementSet measurementsOf(Map<String, BigDecimal> values) {
        MeasurementSet measurements = MeasurementSet.empty();
        for (Map.Entry<String, BigDecimal> entry : required(values, "measurements").entrySet()) {
            measurements = measurements.with(MetricKey.of(entry.getKey()),
                    MetricValue.of(required(entry.getValue(), "measurement " + entry.getKey())));
        }
        return measurements;
    }

    private static JudgeEvaluations evaluationsOf(List<EvaluationRequest> values) {
        return JudgeEvaluationsRequestMapper.toDomain(values);
    }

    private static List<IncidentReport> incidentsOf(List<IncidentRequest> values) {
        return required(values, "incidents (an empty list if there are none)").stream()
                .map(value -> {
                    IncidentRequest incident = required(value, "incident");
                    return new IncidentReport(PenaltyCode.of(required(incident.code(), "incident code")),
                            required(incident.occurrences(), "incident occurrences"));
                })
                .toList();
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " is required");
        }
        return value;
    }

    // ---- respuestas ---------------------------------------------------------------------------------

    static RunResponse toResponse(RunResult run) {
        return new RunResponse(run.id().value(), run.roundId().value(), run.heatId().value(), run.teamId().value(),
                run.challengeId().value(), run.rulebookVersion().toString(), run.attemptNumber().value(),
                run.capturedAt().toString(), run.status().name(), measurementsOf(run.currentMeasurements()),
                incidentReportsOf(run.currentIncidents()), evaluationsResponseOf(run.currentEvaluations()),
                evaluationsResponseOf(run.evaluations()), measurementsOf(run.originalMeasurements()), incidentReportsOf(run.originalIncidents()),
                run.corrections().entries().stream().map(RunWebMapper::correctionOf).toList(),
                completionOf(run.completion()));
    }

    /** Una corrida que todavía espera alguna fuente no tiene puntaje: sólo se informa qué fuentes faltan. */
    static RunScoreResponse toPendingResponse(RunResult run) {
        RunCompletion completion = run.completion();
        return new RunScoreResponse(run.id().value(), run.teamId().value(), run.challengeId().value(),
                run.rulebookVersion().toString(), completion.status().name(),
                completion.missing().stream().map(Enum::name).toList(), null, null, null, null);
    }

    static RunScoreResponse toResponse(CalculateRunScore.RunScore score) {
        return new RunScoreResponse(score.runId().value(), score.teamId().value(), score.challengeId().value(),
                score.rulebookVersion().toString(), score.completion().status().name(), List.of(),
                score.total().value(), contributionsOf(score.breakdown()),
                score.bonusCap().map(RunWebMapper::bonusCapOf).orElse(null),
                score.bySource().map(RunWebMapper::bySourceOf).orElse(null));
    }

    private static Map<String, BigDecimal> measurementsOf(MeasurementSet measurements) {
        Map<String, BigDecimal> sorted = new TreeMap<>();
        measurements.values().forEach((key, value) -> sorted.put(key.value(), value.amount()));
        return sorted;
    }

    private static List<RunResponse.EvaluationResponse> evaluationsResponseOf(JudgeEvaluations evaluations) {
        return evaluations.evaluations().stream()
                .map(evaluation -> new RunResponse.EvaluationResponse(evaluation.judge().value(),
                        evaluation.criterion().value(), evaluation.score().value()))
                .toList();
    }

    private static List<RunResponse.IncidentResponse> incidentReportsOf(List<IncidentReport> incidents) {
        return incidents.stream()
                .map(incident -> new RunResponse.IncidentResponse(incident.code().value(), incident.occurrences()))
                .toList();
    }

    private static RunResponse.CorrectionResponse correctionOf(ResultCorrection correction) {
        return new RunResponse.CorrectionResponse(correction.appliedAt().toString(), correction.actor().name(),
                correction.reason(), measurementsOf(correction.measurements()), incidentReportsOf(correction.incidents()),
                evaluationsResponseOf(correction.evaluations()),
                new RunResponse.OriginResponse(correction.origin().kind(), correction.origin().description()));
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

    private static RunScoreResponse.BonusCapResponse bonusCapOf(BonusCapOutcome outcome) {
        return new RunScoreResponse.BonusCapResponse(outcome.obtained().value(), outcome.cap().value(),
                outcome.trimmed().value(), outcome.applied().value());
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

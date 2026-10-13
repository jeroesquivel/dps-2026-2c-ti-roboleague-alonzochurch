package com.dps.roboleague.adapter.in.web;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.FindRunResult;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.result.CompletionStatus;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunStatus;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public final class RunResultController {

    private final CaptureRunResult captureRunResult;
    private final RegisterAutomaticMeasurements registerAutomaticMeasurements;
    private final RegisterPanelEvaluations registerPanelEvaluations;
    private final FindRunResult findRunResult;

    public RunResultController(CaptureRunResult captureRunResult,
            RegisterAutomaticMeasurements registerAutomaticMeasurements,
            RegisterPanelEvaluations registerPanelEvaluations, FindRunResult findRunResult) {
        this.captureRunResult = captureRunResult;
        this.registerAutomaticMeasurements = registerAutomaticMeasurements;
        this.registerPanelEvaluations = registerPanelEvaluations;
        this.findRunResult = findRunResult;
    }

    @PostMapping
    public RunResponse capture(@RequestBody CompleteCaptureRequest request) {
        RunId id = captureRunResult.execute(new CaptureRunResult.Command(RoundId.of(request.roundId()),
                TeamId.of(request.teamId()), AttemptNumber.of(request.attemptNumber()),
                RunResultWebMapper.measurements(request.measurements()),
                RunResultWebMapper.evaluations(request.evaluations()),
                RunResultWebMapper.incidents(request.incidents()), Actor.of(request.actor())));
        return response(id);
    }

    @PostMapping("/automatic-measurements")
    public RunResponse registerAutomatic(@RequestBody AutomaticCaptureRequest request) {
        RunId id = registerAutomaticMeasurements.execute(new RegisterAutomaticMeasurements.Command(
                RoundId.of(request.roundId()), TeamId.of(request.teamId()),
                AttemptNumber.of(request.attemptNumber()), RunResultWebMapper.measurements(request.measurements()),
                Actor.of(request.actor())));
        return response(id);
    }

    @PostMapping("/panel-evaluations")
    public RunResponse registerPanel(@RequestBody PanelCaptureRequest request) {
        RunId id = registerPanelEvaluations.execute(new RegisterPanelEvaluations.Command(
                RoundId.of(request.roundId()), TeamId.of(request.teamId()),
                AttemptNumber.of(request.attemptNumber()), RunResultWebMapper.evaluations(request.evaluations()),
                RunResultWebMapper.incidents(request.incidents()), Actor.of(request.actor())));
        return response(id);
    }

    @GetMapping("/{runId}")
    public RunResponse find(@PathVariable("runId") String runId) {
        return response(RunId.of(runId));
    }

    private RunResponse response(RunId id) {
        RunResult result = findRunResult.execute(id);
        return RunResultWebMapper.response(result);
    }

    public record CompleteCaptureRequest(String roundId, String teamId, int attemptNumber,
            Map<String, BigDecimal> measurements, List<EvaluationRequest> evaluations,
            List<IncidentRequest> incidents, String actor) {
    }

    public record AutomaticCaptureRequest(String roundId, String teamId, int attemptNumber,
            Map<String, BigDecimal> measurements, String actor) {
    }

    public record PanelCaptureRequest(String roundId, String teamId, int attemptNumber,
            List<EvaluationRequest> evaluations, List<IncidentRequest> incidents, String actor) {
    }

    public record EvaluationRequest(String judgeId, String criterion, BigDecimal score) {
    }

    public record IncidentRequest(String code, int occurrences) {
    }

    public record RunResponse(String runId, String roundId, String heatId, String teamId, String challengeId,
            int attemptNumber, int rulebookVersion, Instant capturedAt, CompletionStatus completionStatus,
            List<ResultSource> missingSources, List<SourceResponse> sources, Map<String, BigDecimal> measurements,
            List<EvaluationResponse> evaluations, List<IncidentResponse> incidents, RunStatus correctionStatus,
            int correctionCount) {
    }

    public record SourceResponse(ResultSource source, boolean received, String actor, Instant receivedAt) {
    }

    public record EvaluationResponse(String judgeId, String criterion, BigDecimal score) {
    }

    public record IncidentResponse(String code, int occurrences) {
    }
}

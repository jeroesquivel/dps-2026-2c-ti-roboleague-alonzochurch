package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class RunResult {

    private final RunId id;
    private final RoundId roundId;
    private final HeatId heatId;
    private final TeamId teamId;
    private final ChallengeId challengeId;
    private final RulebookVersion rulebookVersion;
    private final AttemptNumber attemptNumber;
    private final Instant capturedAt;
    private final MeasurementSet originalMeasurements;
    private final List<IncidentReport> originalIncidents;
    private final JudgeEvaluations evaluations;
    private CorrectionHistory corrections = CorrectionHistory.empty();

    public RunResult(RunId id, RoundId roundId, HeatId heatId, TeamId teamId, ChallengeId challengeId,
            RulebookVersion rulebookVersion, AttemptNumber attemptNumber, Instant capturedAt,
            MeasurementSet measurements, JudgeEvaluations evaluations, Collection<IncidentReport> incidents) {
        this.id = Objects.requireNonNull(id, "run id is required");
        this.roundId = Objects.requireNonNull(roundId, "round id is required");
        this.heatId = Objects.requireNonNull(heatId, "heat id is required");
        this.teamId = Objects.requireNonNull(teamId, "team id is required");
        this.challengeId = Objects.requireNonNull(challengeId, "challenge id is required");
        this.rulebookVersion = Objects.requireNonNull(rulebookVersion, "rulebook version is required");
        this.attemptNumber = Objects.requireNonNull(attemptNumber, "attempt number is required");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capture timestamp is required");
        this.originalMeasurements = Objects.requireNonNull(measurements, "measurements are required");
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations are required");
        this.originalIncidents = List.copyOf(incidents);
    }

    public void applyCorrection(ResultCorrection correction, ChallengeSpec challenge) {
        Objects.requireNonNull(correction, "correction is required");
        if (!challenge.id().equals(challengeId)) {
            throw new RuleViolationException("run " + id.value() + " belongs to challenge " + challengeId.value()
                    + " and cannot be corrected with the rules of challenge " + challenge.id().value());
        }
        if (correction.appliedAt().isBefore(capturedAt)) {
            throw new RuleViolationException("a correction cannot predate the capture of run " + id.value());
        }
        challenge.validate(correction.measurements());
        challenge.validateIncidents(correction.incidents());
        corrections = corrections.append(correction);
    }

    public MeasurementSet currentMeasurements() {
        return corrections.latest().map(ResultCorrection::measurements).orElse(originalMeasurements);
    }

    public List<IncidentReport> currentIncidents() {
        return corrections.latest().map(ResultCorrection::incidents).orElse(originalIncidents);
    }

    public ScoringContext scoringContext() {
        return new ScoringContext(currentMeasurements(), evaluations, currentIncidents());
    }

    public RunStatus status() {
        return corrections.isEmpty() ? RunStatus.CAPTURED : RunStatus.CORRECTED;
    }

    public RunId id() {
        return id;
    }

    public RoundId roundId() {
        return roundId;
    }

    public HeatId heatId() {
        return heatId;
    }

    public TeamId teamId() {
        return teamId;
    }

    public ChallengeId challengeId() {
        return challengeId;
    }

    public RulebookVersion rulebookVersion() {
        return rulebookVersion;
    }

    public AttemptNumber attemptNumber() {
        return attemptNumber;
    }

    public Instant capturedAt() {
        return capturedAt;
    }

    public MeasurementSet originalMeasurements() {
        return originalMeasurements;
    }

    public List<IncidentReport> originalIncidents() {
        return originalIncidents;
    }

    public JudgeEvaluations evaluations() {
        return evaluations;
    }

    public CorrectionHistory corrections() {
        return corrections;
    }
}

package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
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
    private final CorrectionHistory corrections;

    public RunResult(RunId id, RoundId roundId, HeatId heatId, TeamId teamId, ChallengeId challengeId,
            RulebookVersion rulebookVersion, AttemptNumber attemptNumber, Instant capturedAt,
            MeasurementSet measurements, JudgeEvaluations evaluations, Collection<IncidentReport> incidents) {
        this(id, roundId, heatId, teamId, challengeId, rulebookVersion, attemptNumber, capturedAt, measurements,
                evaluations, incidents, CorrectionHistory.empty());
    }

    private RunResult(RunId id, RoundId roundId, HeatId heatId, TeamId teamId, ChallengeId challengeId,
            RulebookVersion rulebookVersion, AttemptNumber attemptNumber, Instant capturedAt,
            MeasurementSet measurements, JudgeEvaluations evaluations, Collection<IncidentReport> incidents,
            CorrectionHistory corrections) {
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
        this.corrections = corrections;
    }

    public static RunResult capture(RunId id, Round round, TeamId teamId, ChallengeSpec challenge,
            AttemptNumber attemptNumber, Instant capturedAt, MeasurementSet measurements,
            JudgeEvaluations evaluations, List<IncidentReport> incidents) {
        Heat heat = round.heatFor(teamId);
        requireSameChallenge(round.challengeId(), challenge, "round " + round.id().value());
        challenge.requireAttemptWithinLimit(attemptNumber);
        challenge.validate(measurements);
        challenge.validateIncidents(incidents);
        challenge.validateEvaluations(evaluations, heat.judges());
        return new RunResult(id, round.id(), heat.id(), teamId, round.challengeId(), round.rulebookVersion(),
                attemptNumber, capturedAt, measurements, evaluations, incidents);
    }

    public RunResult applyCorrection(ResultCorrection correction, ChallengeSpec challenge) {
        Objects.requireNonNull(correction, "correction is required");
        requireSameChallenge(challengeId, challenge, "run " + id.value());
        if (correction.appliedAt().isBefore(capturedAt)) {
            throw new RuleViolationException("a correction cannot predate the capture of run " + id.value());
        }
        challenge.validate(correction.measurements());
        challenge.validateIncidents(correction.incidents());
        return new RunResult(id, roundId, heatId, teamId, challengeId, rulebookVersion, attemptNumber, capturedAt,
                originalMeasurements, evaluations, originalIncidents, corrections.append(correction));
    }

    private static void requireSameChallenge(ChallengeId expected, ChallengeSpec challenge, String owner) {
        if (!challenge.id().equals(expected)) {
            throw new RuleViolationException(owner + " belongs to challenge " + expected.value()
                    + " and cannot use the rules of challenge " + challenge.id().value());
        }
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

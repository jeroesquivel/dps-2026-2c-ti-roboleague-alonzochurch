package com.dps.roboleague.domain.appeal;

import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Appeal {

    private final AppealId id;
    private final RunId runId;
    private final TeamId teamId;
    private final String claim;
    private final Instant submittedAt;
    private final AppealStatus status;
    private final AppealDecision decision;

    public Appeal(AppealId id, RunId runId, TeamId teamId, String claim, Instant submittedAt) {
        this(id, runId, teamId, claim, submittedAt, AppealStatus.SUBMITTED, null);
    }

    private Appeal(AppealId id, RunId runId, TeamId teamId, String claim, Instant submittedAt, AppealStatus status,
            AppealDecision decision) {
        this.id = Objects.requireNonNull(id, "appeal id is required");
        this.runId = Objects.requireNonNull(runId, "run id is required");
        this.teamId = Objects.requireNonNull(teamId, "team id is required");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submission timestamp is required");
        if (claim == null || claim.isBlank()) {
            throw new InvalidValueException("an appeal requires a claim");
        }
        this.claim = claim;
        this.status = status;
        this.decision = decision;
    }

    public static Appeal file(AppealId id, RunResult run, TeamId teamId, String claim, Instant submittedAt,
            AppealWindow window) {
        if (!run.teamId().equals(teamId)) {
            throw new RuleViolationException("team " + teamId.value() + " cannot appeal a run of another team");
        }
        window.requireOpen(run.requireCompletedAt(), submittedAt);
        return new Appeal(id, run.id(), teamId, claim, submittedAt);
    }

    public Appeal accept(AppealDecision decision) {
        return resolveWith(decision, AppealStatus.ACCEPTED);
    }

    public Appeal reject(AppealDecision decision) {
        return resolveWith(decision, AppealStatus.REJECTED);
    }

    public boolean isPending() {
        return status == AppealStatus.SUBMITTED;
    }

    private Appeal resolveWith(AppealDecision newDecision, AppealStatus newStatus) {
        Objects.requireNonNull(newDecision, "decision is required");
        if (!isPending()) {
            throw new ConflictException("appeal " + id.value() + " was already resolved as " + status);
        }
        if (newDecision.decidedAt().isBefore(submittedAt)) {
            throw new RuleViolationException("a decision cannot predate the submission of appeal " + id.value());
        }
        return new Appeal(id, runId, teamId, claim, submittedAt, newStatus, newDecision);
    }

    public AppealId id() {
        return id;
    }

    public RunId runId() {
        return runId;
    }

    public TeamId teamId() {
        return teamId;
    }

    public String claim() {
        return claim;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public AppealStatus status() {
        return status;
    }

    public Optional<AppealDecision> decision() {
        return Optional.ofNullable(decision);
    }
}

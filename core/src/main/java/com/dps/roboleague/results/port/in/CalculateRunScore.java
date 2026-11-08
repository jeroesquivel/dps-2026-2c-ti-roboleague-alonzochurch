package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.RunCompletion;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.challenge.SourcedScore;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.util.Objects;
import java.util.Optional;

public interface CalculateRunScore {

    RunScore execute(Command command);

    record Command(RunId runId) {
    }


    final class RunScore {

        private final RunId runId;
        private final TeamId teamId;
        private final RulebookVersion rulebookVersion;
        private final RunCompletion completion;
        private final ChallengeSpec challenge;
        private final ScoringContext context;

        public RunScore(RunId runId, TeamId teamId, RulebookVersion rulebookVersion, RunCompletion completion,
                ChallengeSpec challenge, ScoringContext context) {
            this.runId = Objects.requireNonNull(runId, "run id is required");
            this.teamId = Objects.requireNonNull(teamId, "team id is required");
            this.rulebookVersion = Objects.requireNonNull(rulebookVersion, "rulebook version is required");
            this.completion = Objects.requireNonNull(completion, "run completion is required");
            this.challenge = Objects.requireNonNull(challenge, "challenge is required");
            this.context = Objects.requireNonNull(context, "scoring context is required");
        }

        public RunId runId() {
            return runId;
        }

        public TeamId teamId() {
            return teamId;
        }

        public ChallengeId challengeId() {
            return challenge.id();
        }

        public RulebookVersion rulebookVersion() {
            return rulebookVersion;
        }

        public RunCompletion completion() {
            return completion;
        }

        public ScoreBreakdown breakdown() {
            return challenge.score(context);
        }

        public Points total() {
            return breakdown().total();
        }

        public Optional<SourcedScore> bySource() {
            return challenge.scoreBySource(context);
        }

        public Optional<BonusCapOutcome> bonusCap() {
            return challenge.bonusCapOutcome(context);
        }
    }
}

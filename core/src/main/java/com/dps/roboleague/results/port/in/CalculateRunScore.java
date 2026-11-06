package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.result.RunCompletion;
import com.dps.roboleague.rules.challenge.SourcedScore;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.util.Optional;

public interface CalculateRunScore {

    RunScore execute(Command command);

    record Command(RunId runId) {
    }

    record RunScore(RunId runId, TeamId teamId, ChallengeId challengeId, RulebookVersion rulebookVersion,
            ScoreBreakdown breakdown, RunCompletion completion, Optional<SourcedScore> bySource,
            Optional<BonusCapOutcome> bonusCap) {

        public Points total() {
            return breakdown.total();
        }
    }
}

package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.challenge.SourcedScore;
import com.dps.roboleague.domain.result.RunCompletion;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.Optional;

public interface CalculateRunScore {

    RunScore execute(Command command);

    record Command(RunId runId) {
    }

    record RunScore(RunId runId, TeamId teamId, ChallengeId challengeId, RulebookVersion rulebookVersion,
            ScoreBreakdown breakdown, RunCompletion completion, Optional<SourcedScore> bySource) {

        public Points total() {
            return breakdown.total();
        }
    }
}

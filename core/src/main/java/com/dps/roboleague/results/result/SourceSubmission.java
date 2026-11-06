package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.scoring.ScoringContext;
import com.dps.roboleague.shared.JudgeId;
import java.util.Set;

public interface SourceSubmission {

    SourceReceipt receipt();

    void validateAgainst(ChallengeSpec challenge, Set<JudgeId> panel);

    ScoringContext addTo(ScoringContext received);
}

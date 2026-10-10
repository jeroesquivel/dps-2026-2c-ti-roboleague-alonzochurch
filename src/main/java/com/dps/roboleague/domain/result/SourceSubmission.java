package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.shared.JudgeId;
import java.util.Set;

public interface SourceSubmission {

    SourceReceipt receipt();

    void validateAgainst(ChallengeSpec challenge, Set<JudgeId> panel);

    ScoringContext addTo(ScoringContext received);
}

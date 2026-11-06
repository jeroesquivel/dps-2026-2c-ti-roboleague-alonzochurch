package com.dps.roboleague.appeals;

import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.InvalidValueException;
import java.time.Instant;
import java.util.Objects;

public record AppealDecision(Actor reviewer, String rationale, Instant decidedAt) {

    public AppealDecision {
        Objects.requireNonNull(reviewer, "reviewer is required");
        Objects.requireNonNull(decidedAt, "decision timestamp is required");
        if (rationale == null || rationale.isBlank()) {
            throw new InvalidValueException("an appeal decision requires a rationale");
        }
    }
}

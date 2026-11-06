package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.InvalidValueException;

public record BestRounds(int counted, int outOf) implements RoundSelection {

    public static final String CODE = "BEST_ROUNDS";

    public BestRounds {
        if (counted < 1 || counted > outOf) {
            throw new InvalidValueException("the best rounds of a challenge require 1 <= N <= M, but N is "
                    + counted + " and M is " + outOf);
        }
    }

    public static BestRounds of(int counted, int outOf) {
        return new BestRounds(counted, outOf);
    }

    public boolean admitsAnotherRound(int scheduledRounds) {
        return scheduledRounds < outOf;
    }

    @Override
    public void requireRoomForAnotherRound(int scheduledRounds, String challenge, CategoryId categoryId) {
        if (!admitsAnotherRound(scheduledRounds)) {
            throw new ConflictException("challenge " + challenge + " counts " + description() + " and category "
                    + categoryId.value() + " already has " + outOf + " rounds of it scheduled");
        }
    }

    @Override
    public void explain(ChallengeRounds rounds, ExplanationDraft draft) {
        draft.select(rounds.scores().bestOf(this, rounds.challengeId(), rounds.policy().version()));
    }

    public int countedOutOf(int playedRounds) {
        return Math.min(counted, playedRounds);
    }

    @Override
    public String description() {
        return "the best %d of %d rounds".formatted(counted, outOf);
    }
}

package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.InvalidValueException;

public record BestRounds(int counted, int outOf) {

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

    public int countedOutOf(int playedRounds) {
        return Math.min(counted, playedRounds);
    }

    public String description() {
        return "the best %d of %d rounds".formatted(counted, outOf);
    }
}

package com.dps.roboleague.shared;

public record ChallengeId(String value) implements Identifier {

    public ChallengeId {
        Identifier.validate(value, "ChallengeId");
    }

    public static ChallengeId of(String value) {
        return new ChallengeId(value);
    }
}

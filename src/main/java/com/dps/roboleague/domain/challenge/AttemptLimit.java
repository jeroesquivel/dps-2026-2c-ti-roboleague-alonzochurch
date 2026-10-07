package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.InvalidValueException;

public record AttemptLimit(int value) {

    public AttemptLimit {
        if (value < 1) {
            throw new InvalidValueException("an attempt limit requires at least one attempt");
        }
    }

    public static AttemptLimit of(int value) {
        return new AttemptLimit(value);
    }

    public boolean allows(AttemptNumber attempt) {
        return attempt.value() <= value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

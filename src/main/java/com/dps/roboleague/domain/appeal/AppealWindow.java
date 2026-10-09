package com.dps.roboleague.domain.appeal;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AppealWindow(Duration length) {

    public AppealWindow {
        Objects.requireNonNull(length, "appeal window length is required");
        if (length.isZero() || length.isNegative()) {
            throw new InvalidValueException("an appeal window requires a positive length");
        }
    }

    public static AppealWindow of(Duration length) {
        return new AppealWindow(length);
    }

    public void requireOpen(Instant capturedAt, Instant submittedAt) {
        Instant closesAt = capturedAt.plus(length);
        if (submittedAt.isAfter(closesAt)) {
            throw new RuleViolationException("the appeal window of " + length + " closed at " + closesAt);
        }
    }
}

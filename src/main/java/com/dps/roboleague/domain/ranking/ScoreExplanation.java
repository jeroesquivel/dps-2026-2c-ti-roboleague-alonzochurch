package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.util.List;

public record ScoreExplanation(List<ScoreSubtotal> subtotals) {

    public static final String STATED_TOTAL = "STATED_TOTAL";

    public ScoreExplanation {
        subtotals = List.copyOf(subtotals);
        if (subtotals.isEmpty()) {
            throw new InvalidValueException("a score explanation requires at least one subtotal");
        }
    }

    public static ScoreExplanation stated(Points total) {
        return new ScoreExplanation(List.of(ScoreSubtotal.of(STATED_TOTAL, "total recorded without a breakdown",
                total)));
    }

    public Points total() {
        return subtotals.stream().map(ScoreSubtotal::points).reduce(Points.ZERO, Points::plus);
    }
}

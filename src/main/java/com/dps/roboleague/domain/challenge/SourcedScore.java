package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SourcedScore(List<SourceBreakdown> sources, ScoreBreakdown acrossSources) {

    public SourcedScore {
        Objects.requireNonNull(acrossSources, "the contributions across sources are required, even if empty");
        sources = List.copyOf(sources);
        Set<ResultSource> seen = new HashSet<>();
        sources.stream()
                .map(SourceBreakdown::source)
                .filter(source -> !seen.add(source))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("source " + repeated + " is grouped twice in the score");
                });
    }

    public Points total() {
        return sources.stream().map(SourceBreakdown::subtotal).reduce(acrossSources.total(), Points::plus);
    }
}

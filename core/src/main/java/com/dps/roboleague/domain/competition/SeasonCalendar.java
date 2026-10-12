package com.dps.roboleague.domain.competition;

import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.DateRange;
import java.util.List;
import java.util.Objects;

public record SeasonCalendar(List<Season> seasons) {

    public SeasonCalendar {
        seasons = List.copyOf(seasons);
    }

    public void requireAvailable(DateRange period) {
        Objects.requireNonNull(period, "season period is required");
        seasons.stream()
                .filter(season -> season.period().overlaps(period))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ConflictException("season period " + period + " overlaps season " + existing.name());
                });
    }
}

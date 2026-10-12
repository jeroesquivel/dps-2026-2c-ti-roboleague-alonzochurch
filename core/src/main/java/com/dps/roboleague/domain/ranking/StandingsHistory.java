package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record StandingsHistory(CompetitionId competitionId, CategoryId categoryId, List<Standings> revisions) {

    public StandingsHistory {
        Objects.requireNonNull(competitionId, "competition id is required");
        Objects.requireNonNull(categoryId, "category id is required");
        revisions = List.copyOf(revisions);
        boolean foreign = revisions.stream().anyMatch(revision -> !revision.competitionId().equals(competitionId)
                || !revision.categoryId().equals(categoryId));
        if (foreign) {
            throw new InvalidValueException("the standings history of category " + categoryId.value()
                    + " cannot hold revisions of another category");
        }
    }

    public Standings generate(RulebookVersion version, Instant generatedAt, List<StandingEntry> entries,
            PendingRuns pendingRuns) {
        if (!revisions.isEmpty()) {
            throw new ConflictException("standings already exist for this category, use a recalculation instead");
        }
        return Standings.provisional(competitionId, categoryId, version, generatedAt, entries, pendingRuns);
    }

    public void requireOpenForResults() {
        if (revisions.stream().anyMatch(Standings::isFinal)) {
            throw new ConflictException("standings of category " + categoryId.value()
                    + " were published as final: results can only change through an appeal");
        }
    }
}

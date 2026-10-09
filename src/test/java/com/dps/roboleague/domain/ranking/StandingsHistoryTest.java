package com.dps.roboleague.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandingsHistoryTest {

    private static final CompetitionId COMPETITION = CompetitionId.of("COMP-1");
    private static final CategoryId CATEGORY = CategoryId.of("CAT-1");
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    private static final List<StandingEntry> ENTRIES = List.of(
            new StandingEntry(1, TeamId.of("DELTA"), Points.of(60), List.of()));

    private final Standings provisional = Standings.provisional(COMPETITION, CATEGORY, RulebookVersion.first(), NOW,
            ENTRIES);

    @Test
    void theFirstGenerationOpensRevisionOne() {
        Standings generated = history().generate(RulebookVersion.of(2), NOW, ENTRIES);

        assertEquals(Revision.first(), generated.revision());
        assertEquals(RulebookVersion.of(2), generated.rulebookVersion());
        assertEquals(ENTRIES, generated.entries());
    }

    @Test
    void aCategoryIsGeneratedOnlyOnceAndLaterChangesGoThroughARecalculation() {
        ConflictException error = assertThrows(ConflictException.class,
                () -> history(provisional).generate(RulebookVersion.first(), NOW, ENTRIES));

        assertTrue(error.getMessage().contains("recalculation"));
    }

    @Test
    void aCategoryWithFinalStandingsAcceptsNoNewResultsEvenAfterARecalculationReopensIt() {
        Standings published = provisional.publish();
        Standings reopened = published.supersede(ENTRIES, NOW.plusSeconds(60));

        assertDoesNotThrow(() -> history().requireOpenForResults());
        assertDoesNotThrow(() -> history(provisional).requireOpenForResults());
        assertThrows(ConflictException.class, () -> history(published).requireOpenForResults());
        assertThrows(ConflictException.class, () -> history(published, reopened).requireOpenForResults());
    }

    @Test
    void holdsOnlyRevisionsOfItsOwnCompetitionAndCategory() {
        Standings otherCategory = Standings.provisional(COMPETITION, CategoryId.of("CAT-2"), RulebookVersion.first(),
                NOW, ENTRIES);
        Standings otherCompetition = Standings.provisional(CompetitionId.of("COMP-2"), CATEGORY,
                RulebookVersion.first(), NOW, ENTRIES);

        assertThrows(InvalidValueException.class, () -> history(otherCategory));
        assertThrows(InvalidValueException.class, () -> history(otherCompetition));
    }

    private StandingsHistory history(Standings... revisions) {
        return new StandingsHistory(COMPETITION, CATEGORY, List.of(revisions));
    }
}

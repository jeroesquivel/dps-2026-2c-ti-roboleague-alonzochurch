package com.dps.roboleague.domain.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecalculateStandingsCommandTest {

    private static final CompetitionId COMPETITION = CompetitionId.of("COMPETITION-1");
    private static final CategoryId CATEGORY = CategoryId.of("CATEGORY-1");
    private static final Actor ACTOR = Actor.of("organizer");

    @Test
    void keepsTheReasonItWasGiven() {
        RecalculateStandings.Command command = new RecalculateStandings.Command(COMPETITION, CATEGORY,
                "a judge corrected a score", ACTOR);

        assertEquals("a judge corrected a score", command.reason());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void requiresAReason(String reason) {
        assertThrows(InvalidValueException.class,
                () -> new RecalculateStandings.Command(COMPETITION, CATEGORY, reason, ACTOR));
    }
}

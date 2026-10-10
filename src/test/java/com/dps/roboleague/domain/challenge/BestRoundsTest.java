package com.dps.roboleague.domain.challenge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.support.RescueEditionFixture;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BestRoundsTest {

    private static final CategoryId JUNIOR = CategoryId.of("JUNIOR");

    @Test
    void aPublishedRulebookExposesTheBestRoundsOfItsChallenge() {
        ChallengeSpec challenge = RescueEditionFixture.rescueChallengeCountingBestRounds(BestRounds.of(3, 5));

        Rulebook rulebook = Rulebook.of(CompetitionId.of("COMP"), RulebookVersion.first(), LocalDate.of(2026, 3, 1),
                RescueEditionFixture.rulebook(challenge));

        assertEquals(Optional.of(BestRounds.of(3, 5)),
                rulebook.challenge(RescueEditionFixture.CHALLENGE_ID).bestRounds());
        assertEquals(Optional.of(BestRounds.of(3, 5)), rulebook.bestRoundsOf(RescueEditionFixture.CHALLENGE_ID));
        assertEquals("the best 3 of 5 rounds", BestRounds.of(3, 5).description());
        assertEquals("BEST_ROUNDS", BestRounds.CODE);
    }

    @Test
    void aChallengeBuiltWithTheOriginalConstructorCountsEveryRound() {
        assertTrue(RescueEditionFixture.rescueChallenge().bestRounds().isEmpty());
    }

    @Test
    void aChallengeThatTheRulebookDoesNotDefineHasNoBestRounds() {
        Rulebook rulebook = Rulebook.of(CompetitionId.of("COMP"), RulebookVersion.first(), LocalDate.of(2026, 3, 1),
                RescueEditionFixture.rulebook());

        assertTrue(rulebook.bestRoundsOf(ChallengeId.of("UNKNOWN")).isEmpty());
        assertTrue(rulebook.bestRoundsOf(RescueEditionFixture.CHALLENGE_ID).isEmpty());
    }

    @Test
    void countingEveryRoundIsAValidConfiguration() {
        assertDoesNotThrow(() -> BestRounds.of(3, 3));
        assertDoesNotThrow(() -> BestRounds.of(1, 1));
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 3", "4, 3", "-1, 2", "1, 0"})
    void rejectsAnythingOutsideOneToMRounds(int counted, int outOf) {
        assertThrows(InvalidValueException.class, () -> BestRounds.of(counted, outOf));
    }

    @Test
    void countsAtMostNOfThePlayedRounds() {
        BestRounds bestThreeOfFive = BestRounds.of(3, 5);

        assertEquals(3, bestThreeOfFive.countedOutOf(5));
        assertEquals(3, bestThreeOfFive.countedOutOf(3));
        assertEquals(2, bestThreeOfFive.countedOutOf(2));
        assertEquals(0, bestThreeOfFive.countedOutOf(0));
    }

    @Test
    void admitsRoundsOnlyUntilMAreScheduled() {
        BestRounds bestTwoOfThree = BestRounds.of(2, 3);

        assertTrue(bestTwoOfThree.admitsAnotherRound(2));
        assertFalse(bestTwoOfThree.admitsAnotherRound(3));
    }

    @Test
    void aChallengeRefusesARoundBeyondMNamingTheChallengeTheCategoryAndM() {
        ChallengeSpec challenge = RescueEditionFixture.rescueChallengeCountingBestRounds(BestRounds.of(2, 3));

        assertDoesNotThrow(() -> challenge.requireRoomForAnotherRound(2, JUNIOR));
        ConflictException error = assertThrows(ConflictException.class,
                () -> challenge.requireRoomForAnotherRound(3, JUNIOR));

        assertTrue(error.getMessage().contains("Rescue mission"));
        assertTrue(error.getMessage().contains("JUNIOR"));
        assertTrue(error.getMessage().contains("already has 3 rounds"));
    }

    @Test
    void aChallengeWithoutBestRoundsHasNoRoundLimit() {
        assertDoesNotThrow(() -> RescueEditionFixture.rescueChallenge().requireRoomForAnotherRound(100, JUNIOR));
    }

    @Test
    void theBestRoundsConfigurationIsRequiredEvenWhenEmpty() {
        assertThrows(NullPointerException.class, () -> new ChallengeSpec(RescueEditionFixture.CHALLENGE_ID,
                "Rescue mission", RescueEditionFixture.metrics(), RescueEditionFixture.scoringRules(),
                RescueEditionFixture.penalties(), AttemptLimit.of(2), null));
    }
}

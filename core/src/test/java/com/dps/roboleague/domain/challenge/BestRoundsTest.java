package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.ranking.RoundSelection;
import com.dps.roboleague.domain.ranking.BestRounds;
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

        assertEquals(BestRounds.of(3, 5),
                rulebook.challenge(RescueEditionFixture.CHALLENGE_ID).roundSelection());
        assertEquals(BestRounds.of(3, 5), rulebook.roundPolicyOf(RescueEditionFixture.CHALLENGE_ID).selection());
        assertEquals(RulebookVersion.first(), rulebook.roundPolicyOf(RescueEditionFixture.CHALLENGE_ID).version());
        assertEquals(rulebook.attemptAggregation(),
                rulebook.roundPolicyOf(RescueEditionFixture.CHALLENGE_ID).aggregation());
        assertEquals("the best 3 of 5 rounds", BestRounds.of(3, 5).description());
        assertEquals("BEST_ROUNDS", BestRounds.CODE);
    }

    @Test
    void aChallengeWithoutExplicitSelectionCountsEveryRound() {
        assertEquals(RoundSelection.all(), RescueEditionFixture.rescueChallenge().roundSelection());
    }

    @Test
    void aChallengeThatTheRulebookDoesNotDefineHasNoBestRounds() {
        Rulebook rulebook = Rulebook.of(CompetitionId.of("COMP"), RulebookVersion.first(), LocalDate.of(2026, 3, 1),
                RescueEditionFixture.rulebook());

        assertEquals(RoundSelection.all(), rulebook.roundPolicyOf(ChallengeId.of("UNKNOWN")).selection());
        assertEquals(RoundSelection.all(), rulebook.roundPolicyOf(RescueEditionFixture.CHALLENGE_ID).selection());
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
    void countingEveryRoundHasNoRoundLimitAndDescribesItself() {
        RoundSelection every = RoundSelection.all();

        assertDoesNotThrow(() -> every.requireRoomForAnotherRound(100, "Rescue mission", JUNIOR));
        assertEquals("all the rounds", every.description());
    }

    @Test
    void theRoundSelectionIsRequired() {
        assertThrows(NullPointerException.class, () -> ChallengeSpec.builder(RescueEditionFixture.CHALLENGE_ID,
                "Rescue mission")
                .metrics(RescueEditionFixture.metrics())
                .scoringRules(RescueEditionFixture.scoringRules())
                .penalties(RescueEditionFixture.penalties())
                .maximumAttempts(AttemptLimit.of(2))
                .roundSelection(null)
                .build());
    }
}

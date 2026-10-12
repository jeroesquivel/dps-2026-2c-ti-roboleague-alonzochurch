package com.dps.roboleague.domain.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.support.RescueEditionFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompetitionScheduleTest {

    private static final CategoryId JUNIOR = CategoryId.of("JUNIOR");
    private static final CategoryId SENIOR = CategoryId.of("SENIOR");
    private static final ChallengeId SPRINT = ChallengeId.of("SPRINT");

    private final ChallengeSpec bestTwoOfThree = RescueEditionFixture
            .rescueChallengeCountingBestRounds(BestRounds.of(2, 3));

    @Test
    void onlyTheRoundsOfTheSameChallengeInTheSameCategoryCountTowardsM() {
        CompetitionSchedule schedule = new CompetitionSchedule(List.of(
                round("R1", JUNIOR, RescueEditionFixture.CHALLENGE_ID, 1),
                round("R2", JUNIOR, RescueEditionFixture.CHALLENGE_ID, 2),
                round("R3", JUNIOR, SPRINT, 3),
                round("R4", SENIOR, RescueEditionFixture.CHALLENGE_ID, 1)));

        assertDoesNotThrow(() -> schedule.requireRoomForRound(JUNIOR, bestTwoOfThree));
        assertDoesNotThrow(() -> schedule.requireRoomForRound(SENIOR, bestTwoOfThree));
    }

    @Test
    void refusesARoundOnceTheCategoryAlreadyHasMRoundsOfTheChallenge() {
        CompetitionSchedule schedule = new CompetitionSchedule(List.of(
                round("R1", JUNIOR, RescueEditionFixture.CHALLENGE_ID, 1),
                round("R2", JUNIOR, RescueEditionFixture.CHALLENGE_ID, 2),
                round("R3", JUNIOR, RescueEditionFixture.CHALLENGE_ID, 3)));

        assertThrows(ConflictException.class, () -> schedule.requireRoomForRound(JUNIOR, bestTwoOfThree));
        assertDoesNotThrow(() -> schedule.requireRoomForRound(JUNIOR, RescueEditionFixture.rescueChallenge()));
    }

    private static Round round(String id, CategoryId category, ChallengeId challenge, int ordinal) {
        return new Round(RoundId.of(id), CompetitionId.of("COMP"), category, challenge, RoundOrdinal.of(ordinal),
                RulebookVersion.first());
    }
}

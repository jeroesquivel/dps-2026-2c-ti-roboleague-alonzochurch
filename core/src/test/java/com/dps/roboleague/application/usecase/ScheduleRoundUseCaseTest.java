package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.ScheduleConflict;
import com.dps.roboleague.domain.schedule.ScheduleConflictException;
import com.dps.roboleague.domain.schedule.ScheduleConflictType;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.TeamFixtures;
import com.dps.roboleague.support.TestEdition;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleRoundUseCaseTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 3, 2, 10, 0);

    private final TestEdition edition = TestEdition.start();

    @Test
    void schedulesOneHeatPerTeamWithItsArenaAndJudges() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");

        RoundId roundId = edition.scheduleRoundFor(1, List.of(delta, omega));

        Round round = edition.round(roundId);

        assertEquals(2, round.heats().entries().size());
        assertEquals(RulebookVersion.first(), round.rulebookVersion());
        assertEquals(delta, round.heatFor(delta).teamId());
        assertEquals(2, round.heatFor(omega).judges().size());
    }

    @Test
    void reusesTheTeamArenaAndJudgesWhenThePreviousHeatEnds() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        RoundId first = edition.scheduleRound(1, List.of(edition.heat(delta, "A1", TEN)));

        RoundId next = edition.scheduleRound(2, List.of(edition.heat(delta, "A1", TEN.plusMinutes(15))));

        assertEquals(edition.round(first).heatFor(delta).slot().end(),
                edition.round(next).heatFor(delta).slot().start());
        assertEquals(1, edition.round(first).heats().entries().size());
        assertEquals(1, edition.round(next).heats().entries().size());
    }

    @Test
    void schedulesSimultaneousHeatsWithIndependentTeamsArenasAndJudges() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");
        ScheduleRound.HeatDraft omegaHeat = new ScheduleRound.HeatDraft(omega, ArenaId.of("A2"),
                new TimeSlot(TEN, Duration.ofMinutes(15)), Set.of(JudgeId.of("J3")));

        RoundId roundId = edition.scheduleRound(1, List.of(edition.heat(delta, "A1", TEN), omegaHeat));

        Round round = edition.round(roundId);
        assertEquals(2, round.heats().entries().size());
        assertEquals(round.heatFor(delta).slot(), round.heatFor(omega).slot());
        assertEquals(ArenaId.of("A2"), round.heatFor(omega).arenaId());
        assertEquals(Set.of(JudgeId.of("J3")), round.heatFor(omega).judges());
    }

    @Test
    void rejectsConflictingHeatsInOneRequestWithoutKeepingAPartialRound() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");

        assertThrows(ScheduleConflictException.class, () -> edition.scheduleRound(1,
                List.of(edition.heat(delta, "A1", TEN), edition.heat(omega, "A1", TEN.plusMinutes(5)))));

        RoundId retried = edition.scheduleRound(1, List.of(edition.heat(delta, "A1", TEN)));
        assertEquals(1, edition.round(retried).heats().entries().size());
        assertEquals(delta, edition.round(retried).heats().entries().getFirst().teamId());
    }

    @Test
    void rejectsAHeatThatReusesAnArenaAlreadyBooked() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");
        edition.scheduleRound(1, List.of(edition.heat(delta, "A1", TEN)));

        ScheduleConflictException error = assertThrows(ScheduleConflictException.class,
                () -> edition.scheduleRound(2, List.of(edition.heat(omega, "A1", TEN.plusMinutes(5)))));

        assertTrue(error.getMessage().contains(ScheduleConflictType.ARENA_BUSY.name()));
        assertTrue(error.conflicts().stream().map(ScheduleConflict::type).toList()
                .contains(ScheduleConflictType.ARENA_BUSY));
    }

    @Test
    void rejectsAHeatScheduledOutsideThePeriodOfTheCompetition() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");

        RuleViolationException error = assertThrows(RuleViolationException.class, () -> edition.scheduleRound(1,
                List.of(edition.heat(delta, "A1", LocalDateTime.of(2027, 12, 25, 3, 0)))));

        assertTrue(error.getMessage().contains("outside the period"));
    }

    @Test
    void rejectsAHeatThatStartsInsideThePeriodButEndsAfterIt() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");

        assertThrows(RuleViolationException.class, () -> edition.scheduleRound(1,
                List.of(edition.heat(delta, "A1", TestEdition.LAST_DAY.atTime(23, 55)))));
    }

    @Test
    void rejectsTeamsThatWereNotAcceptedInTheCompetition() {
        TeamId rejected = edition.register("Rookies", TeamFixtures.membersWithUnderageCompetitor(),
                TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments()).teamId();

        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> edition.scheduleRound(1, List.of(edition.heat(rejected, "A1", TEN))));

        assertTrue(error.getMessage().contains("not accepted"));
    }

    @Test
    void rejectsARoundOrdinalThatIsAlreadyScheduledInTheCategory() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");
        edition.scheduleRound(1, List.of(edition.heat(delta, "A1", TEN)));

        ConflictException error = assertThrows(ConflictException.class,
                () -> edition.scheduleRound(1, List.of(edition.heat(omega, "A2", TEN.plusHours(1)))));

        assertTrue(error.getMessage().contains("round 1 is already scheduled"));
        assertEquals(1, edition.round(edition.scheduleRound(2,
                List.of(edition.heat(omega, "A2", TEN.plusHours(1))))).heats().entries().size());
    }
}

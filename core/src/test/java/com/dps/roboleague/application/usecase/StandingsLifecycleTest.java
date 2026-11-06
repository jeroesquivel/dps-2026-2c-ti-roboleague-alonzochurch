package com.dps.roboleague.application.usecase;

import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.ranking.Revision;
import com.dps.roboleague.results.ranking.StandingEntry;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.rules.challenge.AttemptNumber;
import com.dps.roboleague.rules.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.rules.scoring.IncidentReport;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingsLifecycleTest {

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final TeamId omega = edition.registerEligibleTeam("Omega Crew");

    private RoundId roundId;

    @BeforeEach
    void captureTheRoundResults() {
        roundId = edition.scheduleRoundFor(1, List.of(delta, omega));
        edition.capture(roundId, delta, "95.5", 4, "42", List.of(8, 9), List.of());
        edition.capture(roundId, omega, "105", 5, "55", List.of(7, 7),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));
    }

    @Test
    void generatesProvisionalStandingsOrderedByTotalPoints() {
        Standings standings = generate();

        assertFalse(standings.isFinal());
        assertEquals(Revision.first(), standings.revision());
        assertEquals(List.of(omega, delta), standings.entries().stream().map(StandingEntry::teamId).toList());
        assertEquals(Points.of("71.50"), standings.entryFor(omega).orElseThrow().totalPoints());
        assertEquals(Points.of("60.75"), standings.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void publishingTurnsTheStandingsIntoTheFinalResult() {
        generate();

        Standings published = edition.module().publishStandingsUseCase()
                .execute(new PublishStandings.Command(edition.competitionId(), edition.categoryId(),
                        TestEdition.ACTOR));

        assertTrue(published.isFinal());
        assertTrue(edition.latestStandings().isFinal());
    }

    @Test
    void refusesToGenerateTwiceSoThatCorrectionsGoThroughARecalculation() {
        generate();

        ConflictException error = assertThrows(ConflictException.class, this::generate);

        assertTrue(error.getMessage().contains("recalculation"));
    }

    @Test
    void recalculationKeepsTheAttemptPolicyOfTheRulebookThatGeneratedTheStandings() {
        edition.capture(roundId, delta, 2, "95.5", 4, "42", List.of(8, 9), List.of());

        Standings best = generate();

        assertEquals(Points.of("60.75"), best.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(omega, best.entries().getFirst().teamId());

        edition.publishRulebookWith(new SumOfAttempts());
        Standings summed = edition.module().recalculateStandingsUseCase()
                .execute(new RecalculateStandings.Command(edition.competitionId(), edition.categoryId(),
                        "recalculated under the rulebook that was in force", TestEdition.ACTOR));

        assertEquals(Points.of("60.75"), summed.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void aRulebookPublishedAfterwardsDoesNotRewriteTheRunsPlayedUnderTheEarlierOne() {
        edition.capture(roundId, delta, 2, "95.5", 4, "42", List.of(8, 9), List.of());
        edition.publishRulebookWith(new SumOfAttempts());

        Standings afterwards = generate();

        assertEquals(Points.of("60.75"), afterwards.entryFor(delta).orElseThrow().totalPoints());
    }

    @Test
    void aRoundPlayedUnderANewerRulebookIsAggregatedWithTheNewerPolicy() {
        edition.publishRulebookWith(new SumOfAttempts());
        RoundId newerRound = edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 2, List.of(delta));
        edition.capture(newerRound, delta, "95.5", 4, "42", List.of(8, 9), List.of());
        edition.capture(newerRound, delta, 2, "95.5", 4, "42", List.of(8, 9), List.of());

        Standings summed = generate();

        // 60.75 de la ronda bajo v1 (mejor intento) + 121.50 de la ronda bajo v2 (suma de dos intentos)
        assertEquals(Points.of("182.25"), summed.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(delta, summed.entries().getFirst().teamId());
    }

    @Test
    void provisionalStandingsStillAcceptNewRunsButFinalOnesCloseTheCategory() {
        generate();
        RunId secondAttempt = edition.capture(roundId, delta, 2, "95.5", 4, "42", List.of(8, 9), List.of());
        edition.module().publishStandingsUseCase()
                .execute(new PublishStandings.Command(edition.competitionId(), edition.categoryId(),
                        TestEdition.ACTOR));

        ConflictException error = assertThrows(ConflictException.class,
                () -> edition.capture(roundId, omega, 2, "95.5", 4, "42", List.of(8, 9), List.of()));

        assertEquals(AttemptNumber.of(2), edition.runResult(secondAttempt).attemptNumber());
        assertTrue(error.getMessage().contains("final"));
    }

    private Standings generate() {
        return edition.module().generateStandingsUseCase()
                .execute(new GenerateStandings.Command(edition.competitionId(), edition.categoryId(),
                        TestEdition.ACTOR));
    }
}

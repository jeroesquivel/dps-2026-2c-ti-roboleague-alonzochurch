package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.Revision;
import com.dps.roboleague.domain.ranking.RoundOutcome;
import com.dps.roboleague.domain.ranking.RoundStatus;
import com.dps.roboleague.domain.ranking.ScoreExplanation;
import com.dps.roboleague.domain.ranking.ScoreSubtotal;
import com.dps.roboleague.domain.ranking.StandingEntry;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.aggregation.BestAttempt;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.PointsRate;
import com.dps.roboleague.domain.scoring.ScoringRule;
import com.dps.roboleague.domain.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BestRoundsStandingsTest {

    private static final ChallengeId RESCUE = RescueEditionFixture.CHALLENGE_ID;
    private static final ChallengeId SPRINT = ChallengeId.of("SPRINT");
    private static final List<ScoringRule> TEN_PER_OBJECTIVE = List.of(
            new ObjectiveScoringRule(RescueEditionFixture.OBJECTIVES, PointsRate.of(10), 10));

    private final TestEdition edition = TestEdition.start();
    private final TeamId alpha = edition.registerEligibleTeam("Alpha Bots");
    private final TeamId beta = edition.registerEligibleTeam("Beta Crew");

    @Test
    void theBestRoundsDecideTheStandingsEvenWhenAllTheRoundsWouldSayOtherwise() {
        publishRescueCountingBestRounds(2, 3);
        playRescueRounds(List.of(alpha, beta), new int[] {5, 5, 0}, new int[] {6, 3, 3});

        Standings standings = generate();

        assertEquals(List.of(alpha, beta), standings.entries().stream().map(StandingEntry::teamId).toList());
        assertEquals(Points.of(100), totalOf(standings, alpha));
        assertEquals(Points.of(90), totalOf(standings, beta));
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.COUNTED, RoundStatus.DISCARDED),
                statusesOf(selectionOf(standings, alpha)));
        assertEquals("the best 2 of 3 rounds of challenge RESCUE", selectionOf(standings, alpha).description());
    }

    @Test
    void aCategoryAddsUpTheAttemptPolicyAndTheBestRoundsSubtotals() {
        publishRescueCountingBestRounds(2, 3);
        playRescueRounds(List.of(alpha), new int[] {5, 5, 0});
        RoundId sprint = edition.scheduleRoundFor(SPRINT, 4, List.of(alpha));
        edition.capture(sprint, alpha, "95.5", 2, "42", List.of(), List.of());

        ScoreExplanation explanation = generate().entryFor(alpha).orElseThrow().explanation();

        assertEquals(List.of(BestAttempt.CODE, BestRounds.CODE),
                explanation.subtotals().stream().map(ScoreSubtotal::code).toList());
        assertEquals(Points.of(20), explanation.subtotals().getFirst().points());
        assertEquals(Points.of(100), explanation.subtotals().get(1).points());
        assertEquals(Points.of(120), explanation.total());
    }

    @Test
    void anAcceptedAppealCanBringADiscardedRoundBackAndKeepsThePreviousExplanation() {
        publishRescueCountingBestRounds(2, 3);
        List<RunId> runs = playRescueRounds(List.of(beta), new int[] {6, 2, 3}).getFirst();
        Standings original = generate();
        AppealId appealId = edition.module().submitAppealUseCase().execute(new SubmitAppeal.Command(runs.get(1),
                beta, "five more objectives were completed", Actor.of("beta-captain")));

        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appealId, "video review",
                edition.measurements("95.5", 7, "42"), List.of(), Actor.of("head-judge")));

        Standings recalculated = edition.latestStandings();
        assertEquals(Revision.of(2), recalculated.revision());
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.COUNTED, RoundStatus.DISCARDED),
                statusesOf(selectionOf(recalculated, beta)));
        assertEquals(Points.of(130), totalOf(recalculated, beta));
        Standings firstRevision = edition.standingsHistory().getFirst();
        assertEquals(List.of(RoundStatus.COUNTED, RoundStatus.DISCARDED, RoundStatus.COUNTED),
                statusesOf(selectionOf(firstRevision, beta)));
        assertEquals(Points.of(90), totalOf(firstRevision, beta));
        assertEquals(original.entryFor(beta), firstRevision.entryFor(beta));
    }

    @Test
    void aRecalculationKeepsTheBestRoundsOfTheRulebookThatGeneratedTheStandings() {
        RulebookVersion bestTwo = publishRescueCountingBestRounds(2, 3);
        playRescueRounds(List.of(alpha), new int[] {5, 5, 1});
        generate();
        publishRescueCountingBestRounds(3, 3);

        Standings recalculated = edition.module().recalculateStandingsUseCase()
                .execute(new RecalculateStandings.Command(edition.competitionId(), edition.categoryId(),
                        "review after the new rulebook", TestEdition.ACTOR));

        assertEquals(bestTwo, recalculated.rulebookVersion());
        assertEquals(Points.of(100), totalOf(recalculated, alpha));
        assertEquals("the best 2 of 3 rounds of challenge RESCUE", selectionOf(recalculated, alpha).description());
    }

    @Test
    void theScoreOfARunInADiscardedRoundDoesNotChange() {
        publishRescueCountingBestRounds(2, 3);
        RunId discarded = playRescueRounds(List.of(alpha), new int[] {5, 5, 1}).getFirst().get(2);
        Standings standings = generate();

        CalculateRunScore.RunScore score = edition.module().calculateRunScoreUseCase()
                .execute(new CalculateRunScore.Command(discarded));

        RunResult run = edition.runResult(discarded);
        assertEquals(RoundStatus.DISCARDED, statusesOf(selectionOf(standings, alpha)).get(2));
        assertEquals(rescueChallenge(Optional.empty()).score(run.scoringContext()).contributions(),
                score.breakdown().contributions());
        assertEquals(Points.of(10), score.total());
    }

    @Test
    void aChallengeCountingBestRoundsCannotBeScheduledMoreThanMTimesInACategory() {
        publishRescueCountingBestRounds(2, 3);
        edition.scheduleRound(1, List.of());
        edition.scheduleRound(2, List.of());
        edition.scheduleRound(3, List.of());

        ConflictException error = assertThrows(ConflictException.class, () -> edition.scheduleRound(4, List.of()));

        assertTrue(error.getMessage().contains("Rescue mission"));
        assertTrue(error.getMessage().contains(edition.categoryId().value()));
        assertTrue(error.getMessage().contains("already has 3 rounds"));
        RoundId fourth = edition.scheduleRoundFor(SPRINT, 4, List.of());
        assertEquals(SPRINT, edition.round(fourth).challengeId());
    }

    @Test
    void aChallengeWithoutBestRoundsCanBeScheduledWithoutLimit() {
        publishRescueCountingBestRounds(2, 3);

        for (int ordinal = 1; ordinal <= 5; ordinal++) {
            edition.scheduleRoundFor(SPRINT, ordinal, List.of());
        }

        assertEquals(SPRINT, edition.round(edition.scheduleRoundFor(SPRINT, 6, List.of())).challengeId());
    }

    private RulebookVersion publishRescueCountingBestRounds(int counted, int outOf) {
        return edition.publishRulebook(RescueEditionFixture.rulebook(
                List.of(rescueChallenge(Optional.of(BestRounds.of(counted, outOf))), sprintChallenge()),
                new BestAttempt()));
    }

    private static ChallengeSpec rescueChallenge(Optional<BestRounds> bestRounds) {
        return RescueEditionFixture.challenge(RESCUE, "Rescue mission", TEN_PER_OBJECTIVE, bestRounds);
    }

    private static ChallengeSpec sprintChallenge() {
        return RescueEditionFixture.challenge(SPRINT, "Sprint", TEN_PER_OBJECTIVE, Optional.empty());
    }

    private List<List<RunId>> playRescueRounds(List<TeamId> teams, int[]... objectivesPerTeam) {
        List<RoundId> rounds = IntStream.rangeClosed(1, objectivesPerTeam[0].length)
                .mapToObj(ordinal -> edition.scheduleRoundFor(RESCUE, ordinal, teams))
                .toList();
        return IntStream.range(0, teams.size())
                .mapToObj(team -> IntStream.range(0, rounds.size())
                        .mapToObj(round -> edition.capture(rounds.get(round), teams.get(team), "95.5",
                                objectivesPerTeam[team][round], "42", List.of(), List.of()))
                        .toList())
                .toList();
    }

    private Standings generate() {
        return edition.module().generateStandingsUseCase()
                .execute(new GenerateStandings.Command(edition.competitionId(), edition.categoryId(),
                        TestEdition.ACTOR));
    }

    private static Points totalOf(Standings standings, TeamId team) {
        return standings.entryFor(team).orElseThrow().totalPoints();
    }

    private static ScoreSubtotal selectionOf(Standings standings, TeamId team) {
        return standings.entryFor(team).orElseThrow().explanation().subtotals().stream()
                .filter(subtotal -> subtotal.code().equals(BestRounds.CODE))
                .findFirst()
                .orElseThrow();
    }

    private static List<RoundStatus> statusesOf(ScoreSubtotal selection) {
        return selection.rounds().stream().map(RoundOutcome::status).toList();
    }
}

package com.dps.roboleague.domain.result;

import static com.dps.roboleague.support.ShowcaseFixture.CREATIVITY;
import static com.dps.roboleague.support.ShowcaseFixture.TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MixedRunResultTest {

    private static final TeamId TEAM = TeamId.of("TEAM-1");
    private static final Instant TEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final Instant HALF_PAST_ELEVEN = TEN.plusSeconds(5400);
    private static final Actor TRACK = Actor.of("track-operator");
    private static final Actor HEAD_JUDGE = Actor.of("head-judge");
    private static final ChallengeSpec SHOWCASE = ShowcaseFixture.mixedShowcase();

    private final Round round = ShowcaseFixture.scheduledRound("ROUND-1", "HEAT-1", TEAM);
    private final RunResult automaticOnly = RunResult.open(RunId.of("RUN-1"), round, TEAM, SHOWCASE,
            AttemptNumber.first(), automatic(ShowcaseFixture.exampleMeasurements(), TEN));

    @Test
    void theFirstSourceOpensAPendingRunPinnedToTheRound() {
        assertEquals(round.heatFor(TEAM).id(), automaticOnly.heatId());
        assertEquals(round.rulebookVersion(), automaticOnly.rulebookVersion());
        assertEquals(TEN, automaticOnly.capturedAt());
        assertEquals(CompletionStatus.PENDING, automaticOnly.completion().status());
        assertEquals(List.of(ResultSource.JUDGES), automaticOnly.completion().missing());
        assertEquals(ShowcaseFixture.exampleMeasurements(), automaticOnly.originalMeasurements());
        assertEquals(JudgeEvaluations.none(), automaticOnly.evaluations());
    }

    @Test
    void aPendingRunHasNoScoreNoCompletionTimeAndNoCorrection() {
        ResultCorrection correction = new ResultCorrection(HALF_PAST_ELEVEN, HEAD_JUDGE, "video review",
                ShowcaseFixture.exampleMeasurements(), List.of(), AppealId.of("APPEAL-1"));

        RuleViolationException error = assertThrows(RuleViolationException.class, automaticOnly::scoringContext);

        assertTrue(error.getMessage().contains("JUDGES"));
        assertThrows(RuleViolationException.class, automaticOnly::requireCompletedAt);
        assertThrows(RuleViolationException.class, () -> automaticOnly.applyCorrection(correction, SHOWCASE));
    }

    @Test
    void theSecondSourceCompletesTheSameRunWithoutTouchingThePendingOne() {
        RunResult complete = automaticOnly.receive(round, SHOWCASE, panel(HALF_PAST_ELEVEN));

        assertEquals(automaticOnly.id(), complete.id());
        assertEquals(CompletionStatus.COMPLETE, complete.completion().status());
        assertEquals(HALF_PAST_ELEVEN, complete.requireCompletedAt());
        assertEquals(TEN, complete.capturedAt());
        assertEquals(ShowcaseFixture.exampleMeasurements(), complete.originalMeasurements());
        assertEquals(ShowcaseFixture.exampleEvaluations(), complete.evaluations());
        assertEquals(ShowcaseFixture.exampleIncidents(), complete.originalIncidents());
        assertEquals(ShowcaseFixture.exampleContext(), complete.scoringContext());
        assertEquals(CompletionStatus.PENDING, automaticOnly.completion().status());
    }

    @Test
    void thePanelCanArriveFirst() {
        RunResult panelOnly = RunResult.open(RunId.of("RUN-2"), round, TEAM, SHOWCASE, AttemptNumber.first(),
                panel(TEN));

        RunResult complete = panelOnly.receive(round, SHOWCASE, automatic(ShowcaseFixture.exampleMeasurements(),
                HALF_PAST_ELEVEN));

        assertEquals(List.of(ResultSource.AUTOMATIC), panelOnly.completion().missing());
        assertEquals(ShowcaseFixture.exampleContext(), complete.scoringContext());
    }

    @Test
    void aSourceAlreadyRegisteredIsAConflict() {
        MeasurementSet faster = ShowcaseFixture.measurements("60", "0.80");

        assertThrows(ConflictException.class, () -> automaticOnly.receive(round, SHOWCASE,
                automatic(faster, HALF_PAST_ELEVEN)));
        assertEquals(MetricValue.of(70), automaticOnly.originalMeasurements().require(TIME));
    }

    @Test
    void openingValidatesTheTurnTheAttemptAndTheSource() {
        assertThrows(RuleViolationException.class, () -> RunResult.open(RunId.of("RUN-2"), round,
                TeamId.of("TEAM-2"), SHOWCASE, AttemptNumber.first(), panel(TEN)));
        assertThrows(RuleViolationException.class, () -> RunResult.open(RunId.of("RUN-2"), round, TEAM,
                SHOWCASE, AttemptNumber.of(3), panel(TEN)));
        assertThrows(RuleViolationException.class, () -> RunResult.open(RunId.of("RUN-2"), round, TEAM,
                RescueEditionFixture.rescueChallenge(), AttemptNumber.first(), panel(TEN)));
        assertThrows(RuleViolationException.class, () -> RunResult.open(RunId.of("RUN-2"), round, TEAM,
                SHOWCASE, AttemptNumber.first(), automatic(ShowcaseFixture.exampleMeasurements()
                        .with(CREATIVITY, MetricValue.of(8)), TEN)));
    }

    @Test
    void completingRequiresTheChallengeAndTheHeatOfTheRun() {
        Round otherHeat = ShowcaseFixture.scheduledRound("ROUND-1", "HEAT-9", TEAM);

        assertThrows(RuleViolationException.class, () -> automaticOnly.receive(round,
                RescueEditionFixture.rescueChallenge(), panel(HALF_PAST_ELEVEN)));
        assertThrows(RuleViolationException.class,
                () -> automaticOnly.receive(otherHeat, SHOWCASE, panel(HALF_PAST_ELEVEN)));
        assertThrows(RuleViolationException.class, () -> automaticOnly.receive(round, SHOWCASE,
                new PanelSubmission(JudgeEvaluations.none(), List.of(), HEAD_JUDGE, HALF_PAST_ELEVEN)));
    }

    @Test
    void aMixedChallengeCannotBeCapturedInASingleOperation() {
        assertThrows(RuleViolationException.class, () -> RunResult.capture(RunId.of("RUN-2"), round, TEAM,
                SHOWCASE, AttemptNumber.first(), TEN, ShowcaseFixture.exampleMeasurements(),
                ShowcaseFixture.exampleEvaluations(), List.of()));
    }

    @Test
    void aCompletedRunIsCorrectedFromItsCompletionWithAutomaticMeasurementsOnly() {
        RunResult complete = automaticOnly.receive(round, SHOWCASE, panel(HALF_PAST_ELEVEN));
        ResultCorrection beforeCompletion = correction(ShowcaseFixture.measurements("80", "0.80"),
                HALF_PAST_ELEVEN.minusSeconds(60));
        ResultCorrection withCriterion = correction(ShowcaseFixture.measurements("80", "0.80")
                .with(CREATIVITY, MetricValue.of(10)), HALF_PAST_ELEVEN.plusSeconds(60));

        RunResult corrected = complete.applyCorrection(correction(ShowcaseFixture.measurements("80", "0.80"),
                HALF_PAST_ELEVEN.plusSeconds(60)), SHOWCASE);

        assertThrows(RuleViolationException.class, () -> complete.applyCorrection(beforeCompletion, SHOWCASE));
        assertThrows(RuleViolationException.class, () -> complete.applyCorrection(withCriterion, SHOWCASE));
        assertEquals(MetricValue.of(80), corrected.currentMeasurements().require(TIME));
        assertEquals(ShowcaseFixture.exampleEvaluations(), corrected.evaluations());
        assertEquals(complete.completion(), corrected.completion());
    }

    private static AutomaticSubmission automatic(MeasurementSet measurements, Instant at) {
        return new AutomaticSubmission(measurements, TRACK, at);
    }

    private static PanelSubmission panel(Instant at) {
        return new PanelSubmission(ShowcaseFixture.exampleEvaluations(), ShowcaseFixture.exampleIncidents(),
                HEAD_JUDGE, at);
    }

    private static ResultCorrection correction(MeasurementSet measurements, Instant at) {
        return new ResultCorrection(at, HEAD_JUDGE, "video review", measurements, List.of(),
                AppealId.of("APPEAL-1"));
    }
}

package com.dps.roboleague.domain.result;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.ShowcaseFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoundResultsTest {

    private static final TeamId TEAM = TeamId.of("TEAM-1");
    private static final Instant TEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final ChallengeSpec SHOWCASE = ShowcaseFixture.mixedShowcase();

    private final Round round = ShowcaseFixture.scheduledRound("ROUND-1", "HEAT-1", TEAM);
    private final AutomaticSubmission automatic = new AutomaticSubmission(ShowcaseFixture.exampleMeasurements(),
            Actor.of("track-operator"), TEN);
    private final PanelSubmission panel = new PanelSubmission(ShowcaseFixture.exampleEvaluations(), List.of(),
            Actor.of("head-judge"), TEN.plusSeconds(60));

    @Test
    void theFirstSourceOfATurnOpensANewRun() {
        RunResult opened = new RoundResults(List.of()).receive(() -> RunId.of("RUN-7"), round, TEAM,
                AttemptNumber.first(), SHOWCASE, automatic);

        assertEquals(RunId.of("RUN-7"), opened.id());
        assertEquals(CompletionStatus.PENDING, opened.completion().status());
    }

    @Test
    void theSecondSourceCompletesTheRunOfThatTurnOnly() {
        RunResult pending = new RoundResults(List.of()).receive(() -> RunId.of("RUN-1"), round, TEAM,
                AttemptNumber.first(), SHOWCASE, automatic);
        RoundResults results = new RoundResults(List.of(pending));

        RunResult completed = results.receive(() -> RunId.of("RUN-2"), round, TEAM, AttemptNumber.first(),
                SHOWCASE, panel);
        RunResult secondAttempt = results.receive(() -> RunId.of("RUN-3"), round, TEAM, AttemptNumber.of(2),
                SHOWCASE, panel);

        assertEquals(RunId.of("RUN-1"), completed.id());
        assertEquals(CompletionStatus.COMPLETE, completed.completion().status());
        assertEquals(RunId.of("RUN-3"), secondAttempt.id());
        assertEquals(CompletionStatus.PENDING, secondAttempt.completion().status());
    }
}

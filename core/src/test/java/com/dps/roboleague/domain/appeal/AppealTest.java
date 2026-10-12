package com.dps.roboleague.domain.appeal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.AttemptNumber;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AppealTest {

    private static final Actor HEAD_JUDGE = Actor.of("head-judge");
    private static final Instant CAPTURED_AT = Instant.parse("2026-03-02T10:30:00Z");
    private static final Instant SUBMITTED_AT = Instant.parse("2026-03-02T11:00:00Z");
    private static final AppealWindow ONE_HOUR = AppealWindow.of(Duration.ofHours(1));
    private static final RunResult RUN = new RunResult(RunId.of("RUN-1"), RoundId.of("ROUND-1"), HeatId.of("HEAT-1"),
            TeamId.of("TEAM-1"), ChallengeId.of("RESCUE"), RulebookVersion.first(), AttemptNumber.first(),
            CAPTURED_AT, MeasurementSet.empty(), JudgeEvaluations.none(), List.of());

    private final Appeal appeal = Appeal.file(AppealId.of("APPEAL-1"), RUN, TeamId.of("TEAM-1"),
            "the fourth objective was completed", SUBMITTED_AT, ONE_HOUR);

    @Test
    void isSubmittedUntilItIsResolved() {
        assertEquals(AppealStatus.SUBMITTED, appeal.status());
        assertTrue(appeal.isPending());
        assertTrue(appeal.decision().isEmpty());
        assertEquals(RUN.id(), appeal.runId());
    }

    @Test
    void keepsTheDecisionThatAcceptedItInANewAppeal() {
        Appeal accepted = appeal.accept(decision("the video review confirms the objective"));

        assertEquals(AppealStatus.ACCEPTED, accepted.status());
        assertFalse(accepted.isPending());
        assertEquals("the video review confirms the objective", accepted.decision().orElseThrow().rationale());
        assertTrue(appeal.isPending());
    }

    @Test
    void cannotBeResolvedTwice() {
        Appeal rejected = appeal.reject(decision("the claim is not supported by the recordings"));

        assertThrows(ConflictException.class, () -> rejected.accept(decision("second thoughts")));
        assertEquals(AppealStatus.REJECTED, rejected.status());
    }

    @Test
    void rejectsDecisionsThatPredateTheSubmission() {
        AppealDecision early = new AppealDecision(HEAD_JUDGE, "too early", SUBMITTED_AT.minusSeconds(60));

        assertThrows(RuleViolationException.class, () -> appeal.accept(early));
    }

    @Test
    void onlyTheTeamThatRanCanAppealTheRun() {
        RuleViolationException error = assertThrows(RuleViolationException.class,
                () -> Appeal.file(AppealId.of("APPEAL-2"), RUN, TeamId.of("TEAM-2"), "not our run", SUBMITTED_AT,
                        ONE_HOUR));

        assertTrue(error.getMessage().contains("another team"));
    }

    @Test
    void aRunIsAppealedWithinTheWindowCountedFromItsCapture() {
        Instant deadline = CAPTURED_AT.plus(ONE_HOUR.length());

        assertDoesNotThrow(() -> Appeal.file(AppealId.of("APPEAL-2"), RUN, TeamId.of("TEAM-1"), "on time",
                deadline, ONE_HOUR));
        assertThrows(RuleViolationException.class, () -> Appeal.file(AppealId.of("APPEAL-3"), RUN,
                TeamId.of("TEAM-1"), "too late", deadline.plusSeconds(1), ONE_HOUR));
    }

    @Test
    void appealsOfACategoryReportDuplicatesAndPendingResolutions() {
        Appeal rejected = appeal.reject(decision("the claim is not supported by the recordings"));
        Appeal other = new Appeal(AppealId.of("APPEAL-9"), RunId.of("RUN-9"), TeamId.of("TEAM-9"), "claim",
                SUBMITTED_AT);

        assertThrows(ConflictException.class, () -> new Appeals(List.of(rejected)).requireNoneOn(RUN.id()));
        assertDoesNotThrow(() -> new Appeals(List.of(other)).requireNoneOn(RUN.id()));
        assertDoesNotThrow(() -> new Appeals(List.of(rejected)).requireNonePending());
        ConflictException pending = assertThrows(ConflictException.class,
                () -> new Appeals(List.of(rejected, other)).requireNonePending());
        assertTrue(pending.getMessage().contains("APPEAL-9"));
    }

    private AppealDecision decision(String rationale) {
        return new AppealDecision(HEAD_JUDGE, rationale, SUBMITTED_AT.plusSeconds(1800));
    }
}

package com.dps.roboleague.domain.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunCompletionTest {

    private static final Instant TEN = Instant.parse("2026-03-02T10:00:00Z");
    private static final SourceReceipt AUTOMATIC = new SourceReceipt(ResultSource.AUTOMATIC, Actor.of("track"), TEN);
    private static final SourceReceipt PANEL = new SourceReceipt(ResultSource.JUDGES, Actor.of("head-judge"),
            TEN.plusSeconds(5400));

    @Test
    void aSingleCaptureIsCompleteFromTheStart() {
        RunCompletion immediate = RunCompletion.immediate();

        assertTrue(immediate.isComplete());
        assertEquals(CompletionStatus.COMPLETE, immediate.status());
        assertEquals(List.of(), immediate.missing());
        assertEquals(Optional.empty(), immediate.lastReceivedAt());
    }

    @Test
    void waitsForBothSourcesInAnyOrder() {
        RunCompletion awaiting = RunCompletion.awaitingSources();
        RunCompletion panelFirst = awaiting.receive(PANEL);

        assertEquals(List.of(ResultSource.AUTOMATIC, ResultSource.JUDGES), awaiting.missing());
        assertEquals(CompletionStatus.PENDING, panelFirst.status());
        assertEquals(List.of(ResultSource.AUTOMATIC), panelFirst.missing());
        assertEquals(Optional.of(PANEL), panelFirst.receiptOf(ResultSource.JUDGES));
        assertEquals(Optional.empty(), panelFirst.receiptOf(ResultSource.AUTOMATIC));
    }

    @Test
    void isCompletedByTheSecondSourceAndRemembersTheLastReception() {
        RunCompletion complete = RunCompletion.awaitingSources().receive(PANEL).receive(AUTOMATIC);

        assertTrue(complete.isComplete());
        assertEquals(CompletionStatus.COMPLETE, complete.status());
        assertEquals(Optional.of(PANEL.receivedAt()), complete.lastReceivedAt());
    }

    @Test
    void aSourceIsRegisteredOnlyOnce() {
        RunCompletion automatic = RunCompletion.awaitingSources().receive(AUTOMATIC);
        SourceReceipt again = new SourceReceipt(ResultSource.AUTOMATIC, Actor.of("track"), TEN.plusSeconds(60));

        ConflictException error = assertThrows(ConflictException.class, () -> automatic.receive(again));

        assertTrue(error.getMessage().contains("track"));
        assertEquals(List.of(AUTOMATIC), automatic.receipts());
    }

    @Test
    void aSingleCaptureTakesNoSeparateSource() {
        assertThrows(RuleViolationException.class, () -> RunCompletion.immediate().receive(AUTOMATIC));
    }

    @Test
    void holdsOnlyExpectedSourcesEachOnce() {
        Set<ResultSource> onlyAutomatic = Set.of(ResultSource.AUTOMATIC);

        assertThrows(InvalidValueException.class, () -> new RunCompletion(onlyAutomatic, List.of(PANEL)));
        assertThrows(InvalidValueException.class,
                () -> new RunCompletion(onlyAutomatic, List.of(AUTOMATIC, AUTOMATIC)));
    }

    @Test
    void aLaterCompletionKeepsTheReceiptsOfAnEarlierOne() {
        RunCompletion pending = RunCompletion.awaitingSources().receive(AUTOMATIC);
        RunCompletion complete = pending.receive(PANEL);
        RunCompletion otherPanel = pending.receive(new SourceReceipt(ResultSource.JUDGES, Actor.of("judge-2"),
                PANEL.receivedAt()));

        assertTrue(complete.keepsReceiptsOf(pending));
        assertTrue(complete.keepsReceiptsOf(complete));
        assertFalse(pending.keepsReceiptsOf(complete));
        assertFalse(otherPanel.keepsReceiptsOf(complete));
    }
}

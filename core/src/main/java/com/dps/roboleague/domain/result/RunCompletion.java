package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record RunCompletion(Set<ResultSource> expected, List<SourceReceipt> receipts) {

    public RunCompletion {
        expected = Set.copyOf(expected);
        receipts = List.copyOf(receipts);
        Set<ResultSource> received = new HashSet<>();
        Set<ResultSource> awaited = expected;
        receipts.stream()
                .map(SourceReceipt::source)
                .filter(source -> !awaited.contains(source) || !received.add(source))
                .findFirst()
                .ifPresent(invalid -> {
                    throw new InvalidValueException("source " + invalid
                            + " is not expected by the run or was received twice");
                });
    }

    public static RunCompletion immediate() {
        return new RunCompletion(Set.of(), List.of());
    }

    public static RunCompletion awaitingSources() {
        return new RunCompletion(EnumSet.allOf(ResultSource.class), List.of());
    }

    public RunCompletion receive(SourceReceipt receipt) {
        Objects.requireNonNull(receipt, "source receipt is required");
        if (!expected.contains(receipt.source())) {
            throw new RuleViolationException("the run was captured in a single operation and does not take the "
                    + receipt.source() + " source separately");
        }
        receiptOf(receipt.source()).ifPresent(existing -> {
            throw new ConflictException("source " + existing.source() + " was already registered by "
                    + existing.actor() + " at " + existing.receivedAt());
        });
        List<SourceReceipt> appended = new ArrayList<>(receipts);
        appended.add(receipt);
        return new RunCompletion(expected, appended);
    }

    public List<ResultSource> missing() {
        return Arrays.stream(ResultSource.values())
                .filter(expected::contains)
                .filter(source -> receiptOf(source).isEmpty())
                .toList();
    }

    public boolean isComplete() {
        return missing().isEmpty();
    }

    public CompletionStatus status() {
        return isComplete() ? CompletionStatus.COMPLETE : CompletionStatus.PENDING;
    }

    public Optional<SourceReceipt> receiptOf(ResultSource source) {
        return receipts.stream().filter(receipt -> receipt.source() == source).findFirst();
    }

    public Optional<Instant> lastReceivedAt() {
        return receipts.stream().map(SourceReceipt::receivedAt).max(Comparator.naturalOrder());
    }

    public boolean keepsReceiptsOf(RunCompletion earlier) {
        return receipts.containsAll(earlier.receipts);
    }
}

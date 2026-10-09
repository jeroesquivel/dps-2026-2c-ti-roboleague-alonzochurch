package com.dps.roboleague.domain.appeal;

import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.RunId;
import java.util.List;
import java.util.stream.Collectors;

public record Appeals(List<Appeal> entries) {

    public Appeals {
        entries = List.copyOf(entries);
    }

    public void requireNoneOn(RunId runId) {
        entries.stream()
                .filter(appeal -> appeal.runId().equals(runId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new ConflictException("run " + runId.value() + " was already appealed in appeal "
                            + existing.id().value());
                });
    }

    public void requireNonePending() {
        List<Appeal> pending = entries.stream().filter(Appeal::isPending).toList();
        if (!pending.isEmpty()) {
            throw new ConflictException("appeals still pending: " + pending.stream()
                    .map(appeal -> appeal.id().value())
                    .collect(Collectors.joining(", ")));
        }
    }
}

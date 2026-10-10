package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.challenge.ResultSource;
import com.dps.roboleague.domain.shared.Actor;
import java.time.Instant;
import java.util.Objects;

public record SourceReceipt(ResultSource source, Actor actor, Instant receivedAt) {

    public SourceReceipt {
        Objects.requireNonNull(source, "result source is required");
        Objects.requireNonNull(actor, "the actor that registered the source is required");
        Objects.requireNonNull(receivedAt, "reception timestamp is required");
    }
}

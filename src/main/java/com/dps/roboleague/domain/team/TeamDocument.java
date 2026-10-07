package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Objects;

public record TeamDocument(DocumentType type, String reference) {

    public TeamDocument {
        Objects.requireNonNull(type, "document type is required");
        if (reference == null || reference.isBlank()) {
            throw new InvalidValueException("document " + type + " requires a reference");
        }
    }
}

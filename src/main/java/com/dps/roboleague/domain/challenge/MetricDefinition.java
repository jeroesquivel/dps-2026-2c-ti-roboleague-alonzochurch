package com.dps.roboleague.domain.challenge;

import com.dps.roboleague.domain.shared.RuleViolationException;
import java.util.Objects;

public record MetricDefinition(MetricKey key, MetricKind kind, MetricUnit unit, MetricRequirement requirement) {

    public MetricDefinition {
        Objects.requireNonNull(key, "metric key is required");
        Objects.requireNonNull(kind, "metric kind is required");
        Objects.requireNonNull(unit, "metric unit is required");
        Objects.requireNonNull(requirement, "metric requirement is required");
    }

    public static MetricDefinition required(MetricKey key, MetricKind kind, MetricUnit unit) {
        return new MetricDefinition(key, kind, unit, MetricRequirement.REQUIRED);
    }

    public static MetricDefinition optional(MetricKey key, MetricKind kind, MetricUnit unit) {
        return new MetricDefinition(key, kind, unit, MetricRequirement.OPTIONAL);
    }

    public boolean isRequired() {
        return requirement == MetricRequirement.REQUIRED;
    }

    public void validate(MetricValue value) {
        if (!kind.accepts(value.amount())) {
            throw new RuleViolationException(
                    "value " + value + " is not valid for metric " + key.value() + " of kind " + kind);
        }
    }
}

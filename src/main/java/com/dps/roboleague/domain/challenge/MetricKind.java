package com.dps.roboleague.domain.challenge;

import java.math.BigDecimal;

public enum MetricKind {

    TIME_SECONDS {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }
    },
    OBJECTIVE_COUNT {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount) && amount.stripTrailingZeros().scale() <= 0;
        }
    },
    PRECISION_RATIO {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount) && amount.compareTo(BigDecimal.ONE) <= 0;
        }
    },
    RESOURCE_UNITS {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }
    },
    JUDGE_CRITERION {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }
    };

    public abstract boolean accepts(BigDecimal amount);

    private static boolean isNonNegative(BigDecimal amount) {
        return amount.signum() >= 0;
    }
}

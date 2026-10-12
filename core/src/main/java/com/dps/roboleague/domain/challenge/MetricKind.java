package com.dps.roboleague.domain.challenge;

import java.math.BigDecimal;

public enum MetricKind {

    TIME_SECONDS {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }

        @Override
        public ResultSource source() {
            return ResultSource.AUTOMATIC;
        }
    },
    OBJECTIVE_COUNT {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount) && amount.stripTrailingZeros().scale() <= 0;
        }

        @Override
        public ResultSource source() {
            return ResultSource.AUTOMATIC;
        }
    },
    PRECISION_RATIO {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount) && amount.compareTo(BigDecimal.ONE) <= 0;
        }

        @Override
        public ResultSource source() {
            return ResultSource.AUTOMATIC;
        }
    },
    RESOURCE_UNITS {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }

        @Override
        public ResultSource source() {
            return ResultSource.AUTOMATIC;
        }
    },
    JUDGE_CRITERION {
        @Override
        public boolean accepts(BigDecimal amount) {
            return isNonNegative(amount);
        }

        @Override
        public ResultSource source() {
            return ResultSource.JUDGES;
        }
    };

    public abstract boolean accepts(BigDecimal amount);

    public abstract ResultSource source();

    private static boolean isNonNegative(BigDecimal amount) {
        return amount.signum() >= 0;
    }
}

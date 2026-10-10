package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.Points;

public enum RoundStatus {
    COUNTED {
        @Override
        public Points contributionOf(Points roundPoints) {
            return roundPoints;
        }
    },
    DISCARDED {
        @Override
        public Points contributionOf(Points roundPoints) {
            return Points.ZERO;
        }
    };

    public abstract Points contributionOf(Points roundPoints);
}

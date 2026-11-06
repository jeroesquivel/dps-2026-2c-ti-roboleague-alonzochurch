package com.dps.roboleague.scheduling.application;

import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RoundId;

public final class FindRoundUseCase implements FindRound {

    private final RoundRepository rounds;

    public FindRoundUseCase(RoundRepository rounds) {
        this.rounds = rounds;
    }

    @Override
    public Round execute(RoundId roundId) {
        return rounds.findById(roundId)
                .orElseThrow(() -> NotFoundException.of("Round", roundId.value()));
    }
}

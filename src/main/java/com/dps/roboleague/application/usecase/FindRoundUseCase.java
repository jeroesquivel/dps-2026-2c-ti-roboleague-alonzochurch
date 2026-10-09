package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.port.in.FindRound;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RoundId;

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

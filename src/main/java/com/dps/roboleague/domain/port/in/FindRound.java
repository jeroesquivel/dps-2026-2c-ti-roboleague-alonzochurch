package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.shared.RoundId;

public interface FindRound {

    Round execute(RoundId roundId);
}

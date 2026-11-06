package com.dps.roboleague.scheduling.port.in;

import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.RoundId;

public interface FindRound {

    Round execute(RoundId roundId);
}

package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;

public interface RecalculateStandings {

    Standings execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId, String reason, Actor actor) {

        public Command {
            if (reason == null || reason.isBlank()) {
                throw new InvalidValueException("a standings recalculation requires a reason");
            }
        }
    }
}

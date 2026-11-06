package com.dps.roboleague.results.port.in;

import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.InvalidValueException;

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

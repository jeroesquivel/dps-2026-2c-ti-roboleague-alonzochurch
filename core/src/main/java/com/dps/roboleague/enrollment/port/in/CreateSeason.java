package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.DateRange;
import com.dps.roboleague.shared.SeasonId;

public interface CreateSeason {

    SeasonId execute(Command command);

    record Command(String name, int year, DateRange period, Actor actor) {
    }
}

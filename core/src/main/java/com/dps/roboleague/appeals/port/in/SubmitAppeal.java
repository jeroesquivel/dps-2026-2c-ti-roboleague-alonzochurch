package com.dps.roboleague.appeals.port.in;

import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;

public interface SubmitAppeal {

    AppealId execute(Command command);

    record Command(RunId runId, TeamId teamId, String claim, Actor actor) {
    }
}

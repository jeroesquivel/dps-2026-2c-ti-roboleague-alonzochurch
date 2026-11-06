package com.dps.roboleague.appeals.port.in;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;

public interface RejectAppeal {

    Appeal execute(Command command);

    record Command(AppealId appealId, String rationale, Actor reviewer) {
    }
}

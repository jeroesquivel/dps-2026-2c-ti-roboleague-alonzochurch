package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;

public interface RejectAppeal {

    Appeal execute(Command command);

    record Command(AppealId appealId, String rationale, Actor reviewer) {
    }
}

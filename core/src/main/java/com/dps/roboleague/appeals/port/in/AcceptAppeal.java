package com.dps.roboleague.appeals.port.in;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;

public interface AcceptAppeal {

    Appeal execute(Command command);

    record Command(AppealId appealId, String rationale, ResultAmendment amendment, Actor reviewer) {
    }
}

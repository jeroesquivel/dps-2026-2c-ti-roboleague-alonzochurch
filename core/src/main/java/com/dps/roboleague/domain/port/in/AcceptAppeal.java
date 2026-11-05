package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.result.ResultAmendment;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;

public interface AcceptAppeal {

    Appeal execute(Command command);

    record Command(AppealId appealId, String rationale, ResultAmendment amendment, Actor reviewer) {
    }
}

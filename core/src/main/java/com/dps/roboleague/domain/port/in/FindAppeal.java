package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.shared.AppealId;

public interface FindAppeal {

    Appeal execute(AppealId appealId);
}

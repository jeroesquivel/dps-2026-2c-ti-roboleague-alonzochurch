package com.dps.roboleague.appeals.port.in;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.shared.AppealId;

public interface FindAppeal {

    Appeal execute(AppealId appealId);
}

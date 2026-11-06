package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.TeamId;

public interface FindTeamRegistration {

    TeamRegistration execute(TeamId teamId);
}

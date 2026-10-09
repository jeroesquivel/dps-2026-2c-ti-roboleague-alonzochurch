package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.TeamRegistration;

public interface FindTeamRegistration {

    TeamRegistration execute(TeamId teamId);
}

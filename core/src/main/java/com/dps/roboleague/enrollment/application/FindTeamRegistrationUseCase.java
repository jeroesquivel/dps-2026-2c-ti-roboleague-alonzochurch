package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.TeamId;

public final class FindTeamRegistrationUseCase implements FindTeamRegistration {

    private final TeamRegistrationRepository registrations;

    public FindTeamRegistrationUseCase(TeamRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Override
    public TeamRegistration execute(TeamId teamId) {
        return registrations.findById(teamId)
                .orElseThrow(() -> NotFoundException.of("TeamRegistration", teamId.value()));
    }
}

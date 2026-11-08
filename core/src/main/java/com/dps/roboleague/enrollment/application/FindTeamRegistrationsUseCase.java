package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.TeamId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class FindTeamRegistrationsUseCase implements FindTeamRegistrations {

    private final TeamRegistrationRepository registrations;

    public FindTeamRegistrationsUseCase(TeamRegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Override
    public Map<TeamId, TeamRegistration> execute(Command command) {
        Map<TeamId, TeamRegistration> found = registrations.findAllById(command.teamIds()).stream()
                .collect(Collectors.toUnmodifiableMap(TeamRegistration::id, Function.identity()));
        command.teamIds().stream()
                .filter(teamId -> !found.containsKey(teamId))
                .findFirst()
                .ifPresent(missing -> {
                    throw NotFoundException.of("TeamRegistration", missing.value());
                });
        return found;
    }
}

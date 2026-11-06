package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.TeamId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryTeamRegistrationRepository implements TeamRegistrationRepository {

    private final Map<TeamId, TeamRegistration> registrations = new LinkedHashMap<>();

    @Override
    public void save(TeamRegistration registration) {
        registrations.put(registration.id(), registration);
    }

    @Override
    public Optional<TeamRegistration> findById(TeamId id) {
        return Optional.ofNullable(registrations.get(id));
    }
}

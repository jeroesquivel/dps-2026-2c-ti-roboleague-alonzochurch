package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Override
    public List<TeamRegistration> findByCategory(CompetitionId competitionId, CategoryId categoryId) {
        return registrations.values().stream()
                .filter(registration -> registration.competitionId().equals(competitionId)
                        && registration.categoryId().equals(categoryId))
                .sorted(Comparator.comparing(TeamRegistration::name))
                .toList();
    }
}

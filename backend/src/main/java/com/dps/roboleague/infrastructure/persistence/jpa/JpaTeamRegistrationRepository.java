package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class JpaTeamRegistrationRepository implements TeamRegistrationRepository {

    private final TeamRegistrationRows rows;
    private final AggregateJson json;

    public JpaTeamRegistrationRepository(TeamRegistrationRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(TeamRegistration registration) {
        rows.save(new TeamRegistrationRow(registration.id().value(), registration.competitionId().value(),
                registration.categoryId().value(), json.write(registration)));
    }

    @Override
    public Optional<TeamRegistration> findById(TeamId id) {
        return rows.findById(id.value()).map(row -> json.read(row.payload(), TeamRegistration.class));
    }

    /** Una sola consulta ({@code WHERE id IN (...)}), cualquiera sea la cantidad de ids. */
    @Override
    public List<TeamRegistration> findAllById(Set<TeamId> ids) {
        return rows.findAllById(ids.stream().map(TeamId::value).toList()).stream()
                .map(row -> json.read(row.payload(), TeamRegistration.class))
                .toList();
    }

    @Override
    public List<TeamRegistration> findByCategory(CompetitionId competitionId, CategoryId categoryId) {
        return rows.findByCompetitionIdAndCategoryId(competitionId.value(), categoryId.value()).stream()
                .map(row -> json.read(row.payload(), TeamRegistration.class))
                .sorted(Comparator.comparing(TeamRegistration::name))
                .toList();
    }
}

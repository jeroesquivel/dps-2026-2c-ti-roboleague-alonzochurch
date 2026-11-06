package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.shared.TeamId;
import java.util.Optional;

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
}

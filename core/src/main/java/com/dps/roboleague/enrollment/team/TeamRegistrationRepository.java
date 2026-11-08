package com.dps.roboleague.enrollment.team;

import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import java.util.List;
import java.util.Optional;

public interface TeamRegistrationRepository {

    void save(TeamRegistration registration);

    Optional<TeamRegistration> findById(TeamId id);

    /** Las inscripciones de una categoría de la competencia, cualquiera sea su estado, ordenadas por nombre. */
    List<TeamRegistration> findByCategory(CompetitionId competitionId, CategoryId categoryId);
}

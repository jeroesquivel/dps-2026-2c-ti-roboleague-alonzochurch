package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;

/** Los equipos inscriptos en una categoría, con su estado, ordenados por nombre. */
public interface FindCategoryTeams {

    List<TeamRegistration> execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId) {
    }
}

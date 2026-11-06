package com.dps.roboleague.enrollment.port.in;

import com.dps.roboleague.enrollment.competition.RobotClass;
import com.dps.roboleague.shared.AgeRange;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.DateRange;
import com.dps.roboleague.shared.RulebookVersion;
import java.util.List;

public interface FindCompetition {

    View execute(CompetitionId competitionId);

    record View(CompetitionId id, String name, DateRange period, RulebookVersion activeRulebookVersion,
            List<CategoryView> categories) {
    }

    record CategoryView(CategoryId id, String name, AgeRange ageRange, RobotClass robotClass) {
    }
}

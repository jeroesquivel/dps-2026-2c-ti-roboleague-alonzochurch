package com.dps.roboleague.rules.port.in;

import com.dps.roboleague.enrollment.competition.RobotClass;
import com.dps.roboleague.rules.rulebook.RulebookDraft;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AgeRange;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.DateRange;
import com.dps.roboleague.shared.SeasonId;
import java.util.List;

public interface CreateCompetition {

    Result execute(Command command);

    record Command(SeasonId seasonId, String name, DateRange period, List<CategoryDraft> categories,
            RulebookDraft rulebook, Actor actor) {
    }

    record CategoryDraft(String name, AgeRange ageRange, RobotClass robotClass) {
    }

    record Result(CompetitionId competitionId, List<CategoryId> categoryIds) {

        public CategoryId firstCategory() {
            return categoryIds.getFirst();
        }
    }
}

package com.dps.roboleague.scheduling.port.in;

import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import java.util.List;

/** Las rondas programadas de una categoría, ordenadas por ordinal. */
public interface FindCategoryRounds {

    List<Round> execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId) {
    }
}

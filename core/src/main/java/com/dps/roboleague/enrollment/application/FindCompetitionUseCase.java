package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.competition.Category;
import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.NotFoundException;

public final class FindCompetitionUseCase implements FindCompetition {

    private final CompetitionRepository competitions;

    public FindCompetitionUseCase(CompetitionRepository competitions) {
        this.competitions = competitions;
    }

    @Override
    public View execute(CompetitionId competitionId) {
        Competition competition = competitions.findById(competitionId)
                .orElseThrow(() -> NotFoundException.of("Competition", competitionId.value()));
        return new View(competition.id(), competition.name(), competition.period(),
                competition.activeRulebookVersion(),
                competition.categories().stream().map(FindCompetitionUseCase::viewOf).toList());
    }

    private static CategoryView viewOf(Category category) {
        return new CategoryView(category.id(), category.name(), category.ageRange(), category.robotClass());
    }
}

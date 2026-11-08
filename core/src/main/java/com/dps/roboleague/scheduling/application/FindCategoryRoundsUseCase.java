package com.dps.roboleague.scheduling.application;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.port.in.FindCategoryRounds;
import com.dps.roboleague.shared.NotFoundException;
import java.util.List;

public final class FindCategoryRoundsUseCase implements FindCategoryRounds {

    private final CompetitionRepository competitions;
    private final RoundRepository rounds;

    public FindCategoryRoundsUseCase(CompetitionRepository competitions, RoundRepository rounds) {
        this.competitions = competitions;
        this.rounds = rounds;
    }

    @Override
    public List<Round> execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));
        competition.category(command.categoryId());
        return rounds.findByCategory(competition.id(), command.categoryId());
    }
}

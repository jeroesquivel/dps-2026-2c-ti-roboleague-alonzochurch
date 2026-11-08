package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.shared.NotFoundException;
import java.util.List;

public final class FindCategoryTeamsUseCase implements FindCategoryTeams {

    private final CompetitionRepository competitions;
    private final TeamRegistrationRepository registrations;

    public FindCategoryTeamsUseCase(CompetitionRepository competitions, TeamRegistrationRepository registrations) {
        this.competitions = competitions;
        this.registrations = registrations;
    }

    @Override
    public List<TeamRegistration> execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));
        competition.category(command.categoryId());
        return registrations.findByCategory(competition.id(), command.categoryId());
    }
}

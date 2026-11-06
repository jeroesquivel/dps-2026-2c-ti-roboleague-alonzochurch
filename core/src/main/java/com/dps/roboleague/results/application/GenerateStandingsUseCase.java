package com.dps.roboleague.results.application;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsHistory;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.rules.rulebook.Rulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

public final class GenerateStandingsUseCase implements GenerateStandings {

    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final StandingsRepository standings;
    private final CategoryScoringService scoringService;
    private final AuditLog auditLog;
    private final Clock clock;

    public GenerateStandingsUseCase(CompetitionRepository competitions, RulebookRepository rulebooks,
            StandingsRepository standings, CategoryScoringService scoringService, AuditLog auditLog, Clock clock) {
        this.competitions = competitions;
        this.rulebooks = rulebooks;
        this.standings = standings;
        this.scoringService = scoringService;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Standings execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));
        competition.category(command.categoryId());
        StandingsHistory history = new StandingsHistory(competition.id(), command.categoryId(),
                standings.findHistory(competition.id(), command.categoryId()));

        RulebookVersion version = competition.activeRulebookVersion();
        Rulebook rulebook = rulebooks.find(competition.id(), version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()));
        Standings generated = history.generate(version, clock.instant(),
                scoringService.rank(competition.id(), command.categoryId(), rulebook),
                scoringService.pendingRuns(competition.id(), command.categoryId()));

        standings.save(generated);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.STANDINGS_GENERATED,
                command.categoryId(), command.actor(), Map.of(AuditDetail.RULEBOOK, version.toString())));
        return generated;
    }
}

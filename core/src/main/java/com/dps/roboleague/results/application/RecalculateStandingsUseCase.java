package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.rules.rulebook.Rulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

public final class RecalculateStandingsUseCase implements RecalculateStandings {

    private final StandingsRepository standings;
    private final RulebookRepository rulebooks;
    private final CategoryScoringService scoringService;
    private final AuditLog auditLog;
    private final Clock clock;

    public RecalculateStandingsUseCase(StandingsRepository standings, RulebookRepository rulebooks,
            CategoryScoringService scoringService, AuditLog auditLog, Clock clock) {
        this.standings = standings;
        this.rulebooks = rulebooks;
        this.scoringService = scoringService;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Standings execute(Command command) {
        Standings current = standings.findLatest(command.competitionId(), command.categoryId())
                .orElseThrow(() -> NotFoundException.of("Standings", command.categoryId().value()));
        Rulebook rulebook = rulebooks.find(command.competitionId(), current.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", current.rulebookVersion().toString()));

        Standings recalculated = current.supersede(
                scoringService.rank(command.competitionId(), command.categoryId(), rulebook),
                scoringService.pendingRuns(command.competitionId(), command.categoryId()), clock.instant());
        standings.save(recalculated);

        auditLog.record(new AuditEvent(clock.instant(), AuditAction.STANDINGS_RECALCULATED,
                command.categoryId(), command.actor(),
                Map.of(AuditDetail.REASON, command.reason(), AuditDetail.REVISION, recalculated.revision().toString(),
                        AuditDetail.RULEBOOK, current.rulebookVersion().toString())));
        return recalculated;
    }
}

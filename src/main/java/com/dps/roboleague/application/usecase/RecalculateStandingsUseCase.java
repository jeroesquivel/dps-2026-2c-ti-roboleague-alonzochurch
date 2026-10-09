package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.ranking.CategoryScoringService;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.shared.NotFoundException;
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
                scoringService.rank(command.competitionId(), command.categoryId(), rulebook), clock.instant());
        standings.save(recalculated);

        auditLog.record(new AuditEvent(clock.instant(), AuditAction.STANDINGS_RECALCULATED,
                command.categoryId(), command.actor(),
                Map.of(AuditDetail.REASON, command.reason(), AuditDetail.REVISION, recalculated.revision().toString(),
                        AuditDetail.RULEBOOK, current.rulebookVersion().toString())));
        return recalculated;
    }
}

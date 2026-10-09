package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.appeal.Appeals;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.ranking.CategoryScoringService;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.shared.NotFoundException;
import java.time.Clock;
import java.util.Map;

public final class PublishStandingsUseCase implements PublishStandings {

    private final StandingsRepository standings;
    private final CategoryScoringService scoringService;
    private final AppealRepository appeals;
    private final AuditLog auditLog;
    private final Clock clock;

    public PublishStandingsUseCase(StandingsRepository standings, CategoryScoringService scoringService,
            AppealRepository appeals, AuditLog auditLog, Clock clock) {
        this.standings = standings;
        this.scoringService = scoringService;
        this.appeals = appeals;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Standings execute(Command command) {
        Standings provisional = standings.findLatest(command.competitionId(), command.categoryId())
                .orElseThrow(() -> NotFoundException.of("Standings", command.categoryId().value()));
        new Appeals(scoringService.runsOf(command.competitionId(), command.categoryId()).stream()
                .flatMap(run -> appeals.findByRun(run.id()).stream())
                .toList()).requireNonePending();

        Standings published = provisional.publish();
        standings.save(published);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.STANDINGS_PUBLISHED,
                command.categoryId(), command.actor(),
                Map.of(AuditDetail.REVISION, published.revision().toString())));
        return published;
    }
}

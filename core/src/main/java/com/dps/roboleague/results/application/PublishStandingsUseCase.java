package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.PendingAppeals;
import com.dps.roboleague.results.ranking.PendingRuns;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class PublishStandingsUseCase implements PublishStandings {

    private final StandingsRepository standings;
    private final CategoryScoringService scoringService;
    private final PendingAppeals appeals;
    private final AuditLog auditLog;
    private final Clock clock;

    public PublishStandingsUseCase(StandingsRepository standings, CategoryScoringService scoringService,
            PendingAppeals appeals, AuditLog auditLog, Clock clock) {
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
        List<RunResult> runs = scoringService.runsOf(command.competitionId(), command.categoryId());
        appeals.requireNoneOn(runs.stream().map(RunResult::id).toList());
        PendingRuns.among(runs).requireNone();

        Standings published = provisional.publish();
        standings.save(published);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.STANDINGS_PUBLISHED,
                command.categoryId(), command.actor(),
                Map.of(AuditDetail.REVISION, published.revision().toString())));
        return published;
    }
}

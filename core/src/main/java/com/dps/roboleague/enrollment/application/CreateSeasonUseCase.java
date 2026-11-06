package com.dps.roboleague.enrollment.application;

import com.dps.roboleague.enrollment.competition.Season;
import com.dps.roboleague.enrollment.competition.SeasonCalendar;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.port.in.CreateSeason;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.SeasonId;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;

public final class CreateSeasonUseCase implements CreateSeason {

    private final SeasonRepository seasons;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public CreateSeasonUseCase(SeasonRepository seasons, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.seasons = seasons;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public SeasonId execute(Command command) {
        new SeasonCalendar(seasons.findAll()).requireAvailable(command.period());

        SeasonId id = idGenerator.nextSeasonId();
        seasons.save(new Season(id, command.name(), command.year(), command.period()));
        auditLog.record(AuditEvent.of(clock.instant(), AuditAction.SEASON_CREATED, id, command.actor()));
        return id;
    }
}

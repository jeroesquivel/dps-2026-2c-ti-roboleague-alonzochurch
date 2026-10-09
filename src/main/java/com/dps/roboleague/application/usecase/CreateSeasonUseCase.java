package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.Season;
import com.dps.roboleague.domain.competition.SeasonCalendar;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.SeasonId;
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

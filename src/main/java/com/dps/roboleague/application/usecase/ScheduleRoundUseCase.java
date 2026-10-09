package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.Competition;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.CompetitionSchedule;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.schedule.ScheduleConflictDetector;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.domain.team.TeamRegistrationRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ScheduleRoundUseCase implements ScheduleRound {

    private final CompetitionRepository competitions;
    private final RulebookRepository rulebooks;
    private final TeamRegistrationRepository registrations;
    private final RoundRepository rounds;
    private final ScheduleConflictDetector conflictDetector;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public ScheduleRoundUseCase(CompetitionRepository competitions, RulebookRepository rulebooks,
            TeamRegistrationRepository registrations, RoundRepository rounds,
            ScheduleConflictDetector conflictDetector, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.competitions = competitions;
        this.rulebooks = rulebooks;
        this.registrations = registrations;
        this.rounds = rounds;
        this.conflictDetector = conflictDetector;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public RoundId execute(Command command) {
        Competition competition = competitions.findById(command.competitionId())
                .orElseThrow(() -> NotFoundException.of("Competition", command.competitionId().value()));
        competition.category(command.categoryId());
        RulebookVersion version = competition.activeRulebookVersion();
        rulebooks.find(competition.id(), version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()))
                .challenge(command.challengeId());
        CompetitionSchedule schedule = new CompetitionSchedule(rounds.findByCompetition(competition.id()));
        schedule.requireAvailableOrdinal(command.categoryId(), command.ordinal());

        RoundId roundId = idGenerator.nextRoundId();
        Round round = new Round(roundId, competition.id(), command.categoryId(), command.challengeId(),
                command.ordinal(), version);
        List<Heat> booked = new ArrayList<>(schedule.bookedHeats());
        for (HeatDraft draft : command.heats()) {
            TeamRegistration team = registrations.findById(draft.teamId())
                    .orElseThrow(() -> NotFoundException.of("TeamRegistration", draft.teamId().value()));
            competition.requireSlotWithinPeriod(draft.slot());
            Heat heat = new Heat(idGenerator.nextHeatId(), roundId, draft.teamId(), draft.arenaId(), draft.slot(),
                    draft.judges());
            conflictDetector.requireNoConflicts(booked, heat);
            round = round.schedule(heat, team);
            booked.add(heat);
        }

        rounds.save(round);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.ROUND_SCHEDULED, roundId, command.actor(),
                Map.of(AuditDetail.HEATS, String.valueOf(round.heats().entries().size()),
                        AuditDetail.RULEBOOK, version.toString())));
        return roundId;
    }
}

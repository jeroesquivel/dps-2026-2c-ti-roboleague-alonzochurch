package com.dps.roboleague.scheduling.application;

import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.CompetitionSchedule;
import com.dps.roboleague.scheduling.Heat;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.ScheduleConflictDetector;
import com.dps.roboleague.scheduling.port.in.ScheduleRound;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
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
        ChallengeSpec challenge = rulebooks.find(competition.id(), version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()))
                .challenge(command.challengeId());
        CompetitionSchedule schedule = new CompetitionSchedule(rounds.findByCompetition(competition.id()));
        schedule.requireAvailableOrdinal(command.categoryId(), command.ordinal());
        schedule.requireRoomForRound(command.categoryId(), challenge);

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

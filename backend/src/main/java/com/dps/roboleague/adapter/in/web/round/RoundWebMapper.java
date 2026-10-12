package com.dps.roboleague.adapter.in.web.round;

import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traduce entre los DTOs web y los tipos del núcleo. Sólo verifica presencia y formato; los
 * conflictos de agenda, la elegibilidad de los equipos y los límites de rondas los decide el dominio.
 */
final class RoundWebMapper {

    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RoundWebMapper() {
    }

    static ScheduleRound.Command toCommand(ScheduleRoundRequest request) {
        List<ScheduleRoundRequest.HeatRequest> heats = required(request.heats(), "heats");
        if (heats.isEmpty()) {
            throw new InvalidValueException("a round requires at least one heat");
        }
        return new ScheduleRound.Command(CompetitionId.of(request.competitionId()),
                CategoryId.of(request.categoryId()), ChallengeId.of(request.challengeId()),
                RoundOrdinal.of(required(request.ordinal(), "round ordinal")),
                heats.stream().map(RoundWebMapper::heatOf).toList(), Actor.of(request.actor()));
    }

    static ScheduledRoundResponse toResponse(RoundId roundId) {
        return new ScheduledRoundResponse(roundId.value());
    }

    static RoundResponse toResponse(Round round) {
        return new RoundResponse(round.id().value(), round.competitionId().value(), round.categoryId().value(),
                round.challengeId().value(), round.ordinal().value(), round.rulebookVersion().toString(),
                round.heats().entries().stream().map(RoundWebMapper::heatResponseOf).toList());
    }

    private static ScheduleRound.HeatDraft heatOf(ScheduleRoundRequest.HeatRequest request) {
        ScheduleRoundRequest.HeatRequest heat = required(request, "heat");
        TimeSlot slot = new TimeSlot(startOf(heat.start()),
                Duration.ofMinutes(required(heat.durationMinutes(), "heat duration in minutes")));
        Set<JudgeId> judges = required(heat.judges(), "heat judges").stream()
                .map(judge -> JudgeId.of(required(judge, "judge")))
                .collect(Collectors.toSet());
        if (judges.isEmpty()) {
            throw new InvalidValueException("a heat requires at least one judge");
        }
        return new ScheduleRound.HeatDraft(TeamId.of(heat.teamId()), ArenaId.of(heat.arenaId()), slot, judges);
    }

    private static LocalDateTime startOf(String value) {
        try {
            return LocalDateTime.parse(required(value, "heat start"), LOCAL_DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new InvalidValueException("heat start " + value
                    + " must be an ISO local date-time without zone or offset, for example 2026-03-02T10:00:00");
        }
    }

    private static RoundResponse.HeatResponse heatResponseOf(Heat heat) {
        TimeSlot slot = heat.slot();
        return new RoundResponse.HeatResponse(heat.id().value(), heat.teamId().value(), heat.arenaId().value(),
                LOCAL_DATE_TIME.format(slot.start()), LOCAL_DATE_TIME.format(slot.end()), slot.duration().toMinutes(),
                heat.judges().stream().map(JudgeId::value).sorted().toList());
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " is required");
        }
        return value;
    }
}

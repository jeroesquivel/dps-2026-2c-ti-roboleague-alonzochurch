package com.dps.roboleague.scheduling.port.in;

import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.ArenaId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.JudgeId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RoundOrdinal;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.shared.TimeSlot;
import java.util.List;
import java.util.Set;

public interface ScheduleRound {

    RoundId execute(Command command);

    record Command(CompetitionId competitionId, CategoryId categoryId, ChallengeId challengeId, RoundOrdinal ordinal,
            List<HeatDraft> heats, Actor actor) {
    }

    record HeatDraft(TeamId teamId, ArenaId arenaId, TimeSlot slot, Set<JudgeId> judges) {
    }
}

package com.dps.roboleague.infrastructure.id;

import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.MemberId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.SeasonId;
import com.dps.roboleague.shared.TeamId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SequentialIdGenerator implements IdGenerator {

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public SeasonId nextSeasonId() {
        return SeasonId.of(next("SEASON"));
    }

    @Override
    public CompetitionId nextCompetitionId() {
        return CompetitionId.of(next("COMPETITION"));
    }

    @Override
    public CategoryId nextCategoryId() {
        return CategoryId.of(next("CATEGORY"));
    }

    @Override
    public TeamId nextTeamId() {
        return TeamId.of(next("TEAM"));
    }

    @Override
    public RoundId nextRoundId() {
        return RoundId.of(next("ROUND"));
    }

    @Override
    public HeatId nextHeatId() {
        return HeatId.of(next("HEAT"));
    }

    @Override
    public RunId nextRunId() {
        return RunId.of(next("RUN"));
    }

    @Override
    public AppealId nextAppealId() {
        return AppealId.of(next("APPEAL"));
    }

    @Override
    public MemberId nextMemberId() {
        return MemberId.of(next("MEMBER"));
    }

    private String next(String prefix) {
        return prefix + "-" + counters.computeIfAbsent(prefix, key -> new AtomicInteger()).incrementAndGet();
    }
}

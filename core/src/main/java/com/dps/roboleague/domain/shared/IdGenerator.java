package com.dps.roboleague.domain.shared;


public interface IdGenerator {

    SeasonId nextSeasonId();

    CompetitionId nextCompetitionId();

    CategoryId nextCategoryId();

    TeamId nextTeamId();

    RoundId nextRoundId();

    HeatId nextHeatId();

    RunId nextRunId();

    AppealId nextAppealId();

    MemberId nextMemberId();
}

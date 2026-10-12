package com.dps.roboleague.adapter.in.web.round;

import java.util.List;

public record RoundResponse(String roundId, String competitionId, String categoryId, String challengeId, int ordinal,
        String rulebookVersion, List<HeatResponse> heats) {

    public record HeatResponse(String heatId, String teamId, String arenaId, String start, String end,
            long durationMinutes, List<String> judges) {
    }
}

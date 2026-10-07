package com.dps.roboleague.domain.schedule;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Heats(List<Heat> entries) {

    public Heats {
        entries = List.copyOf(entries);
        Set<TeamId> teams = new HashSet<>();
        entries.stream()
                .map(Heat::teamId)
                .filter(team -> !teams.add(team))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("team " + repeated.value() + " already has a heat in the round");
                });
    }

    public static Heats none() {
        return new Heats(List.of());
    }

    public Heats with(Heat heat) {
        List<Heat> scheduled = new ArrayList<>(entries);
        scheduled.add(heat);
        return new Heats(scheduled);
    }

    public Optional<Heat> heatFor(TeamId teamId) {
        return entries.stream().filter(heat -> heat.teamId().equals(teamId)).findFirst();
    }
}

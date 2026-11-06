package com.dps.roboleague.scheduling;

import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.RoundOrdinal;
import java.util.List;

public record CompetitionSchedule(List<Round> rounds) {

    public CompetitionSchedule {
        rounds = List.copyOf(rounds);
    }

    public void requireAvailableOrdinal(CategoryId categoryId, RoundOrdinal ordinal) {
        boolean taken = rounds.stream()
                .anyMatch(round -> round.categoryId().equals(categoryId) && round.ordinal().equals(ordinal));
        if (taken) {
            throw new ConflictException("round " + ordinal.value() + " is already scheduled in category "
                    + categoryId.value());
        }
    }

    public void requireRoomForRound(CategoryId categoryId, ChallengeSpec challenge) {
        long scheduled = rounds.stream()
                .filter(round -> round.categoryId().equals(categoryId) && round.challengeId().equals(challenge.id()))
                .count();
        challenge.requireRoomForAnotherRound(Math.toIntExact(scheduled), categoryId);
    }

    public List<Heat> bookedHeats() {
        return rounds.stream().flatMap(round -> round.heats().entries().stream()).toList();
    }
}

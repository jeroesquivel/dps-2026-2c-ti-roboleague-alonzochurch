package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.MemberId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record TeamMembers(List<Member> members) {

    public TeamMembers {
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new InvalidValueException("a team requires at least one member");
        }
        Set<MemberId> seen = new HashSet<>();
        members.stream()
                .map(Member::id)
                .filter(id -> !seen.add(id))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("member " + repeated.value() + " is listed twice in the team");
                });
    }

    public static TeamMembers of(Member... members) {
        return new TeamMembers(List.of(members));
    }

    public List<Member> competitors() {
        return members.stream().filter(Member::isCompetitor).toList();
    }

    public boolean hasCoach() {
        return members.stream().anyMatch(Member::isCoach);
    }
}

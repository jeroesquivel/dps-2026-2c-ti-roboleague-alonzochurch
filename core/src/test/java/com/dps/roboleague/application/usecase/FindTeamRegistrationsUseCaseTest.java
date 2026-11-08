package com.dps.roboleague.application.usecase;

import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.TestEdition;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindTeamRegistrationsUseCaseTest {

    private final TestEdition edition = TestEdition.start();

    @Test
    void findsEveryRequestedTeamInOneQuery() {
        TeamId alpha = edition.registerEligibleTeam("Alpha Bots");
        TeamId omega = edition.registerEligibleTeam("Omega Crew");

        Map<TeamId, TeamRegistration> found = find(Set.of(alpha, omega));

        assertEquals(Set.of(alpha, omega), found.keySet());
        assertEquals("Alpha Bots", found.get(alpha).name());
        assertEquals("Omega Crew", found.get(omega).name());
    }

    @Test
    void asksForNothingAndFindsNothing() {
        assertTrue(find(Set.of()).isEmpty());
    }

    @Test
    void failsLikeTheSingleQueryWhenATeamDoesNotExist() {
        TeamId alpha = edition.registerEligibleTeam("Alpha Bots");

        NotFoundException missing = assertThrows(NotFoundException.class,
                () -> find(Set.of(alpha, TeamId.of("TEAM-404"))));
        assertTrue(missing.getMessage().contains("TEAM-404"), missing.getMessage());
    }

    private Map<TeamId, TeamRegistration> find(Set<TeamId> teamIds) {
        return edition.module().findTeamRegistrationsUseCase().execute(new FindTeamRegistrations.Command(teamIds));
    }
}

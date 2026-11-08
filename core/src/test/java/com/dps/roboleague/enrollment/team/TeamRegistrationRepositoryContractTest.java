package com.dps.roboleague.enrollment.team;

import com.dps.roboleague.enrollment.eligibility.EligibilityRuleCode;
import com.dps.roboleague.enrollment.eligibility.EligibilityVerdict;
import com.dps.roboleague.enrollment.eligibility.EligibilityViolation;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.TeamFixtures;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que cualquier adaptador de {@link TeamRegistrationRepository} tiene que cumplir. */
public abstract class TeamRegistrationRepositoryContractTest {

    private static final CompetitionId COMPETITION = CompetitionId.of("COMP-1");
    private static final CompetitionId OTHER_COMPETITION = CompetitionId.of("COMP-2");
    private static final CategoryId JUNIOR = CategoryId.of("CAT-1");
    private static final CategoryId SENIOR = CategoryId.of("CAT-2");

    protected abstract TeamRegistrationRepository repository();

    @Test
    void findsARegistrationByItsId() {
        TeamRegistrationRepository repository = repository();
        TeamRegistration saved = registration("TEAM-1", "Delta Bots", COMPETITION, JUNIOR);
        repository.save(saved);

        assertEquals(saved.name(), repository.findById(TeamId.of("TEAM-1")).orElseThrow().name());
        assertTrue(repository.findById(TeamId.of("TEAM-404")).isEmpty());
    }

    @Test
    void findsSeveralRegistrationsAtOnceLeavingOutTheOnesThatDoNotExist() {
        TeamRegistrationRepository repository = repository();
        repository.save(registration("TEAM-1", "Delta Bots", COMPETITION, JUNIOR));
        repository.save(registration("TEAM-2", "Omega Crew", COMPETITION, SENIOR));
        repository.save(registration("TEAM-3", "Kappa Labs", OTHER_COMPETITION, JUNIOR));

        List<TeamRegistration> found = repository.findAllById(
                Set.of(TeamId.of("TEAM-1"), TeamId.of("TEAM-3"), TeamId.of("TEAM-404")));

        assertEquals(Set.of("Delta Bots", "Kappa Labs"),
                found.stream().map(TeamRegistration::name).collect(Collectors.toSet()));
        assertTrue(repository.findAllById(Set.of()).isEmpty());
    }

    @Test
    void findsTheRegistrationsOfACategoryOrderedByNameWhateverTheirStatus() {
        TeamRegistrationRepository repository = repository();
        repository.save(registration("TEAM-1", "Omega Crew", COMPETITION, JUNIOR));
        repository.save(registration("TEAM-2", "Alpha Bots", COMPETITION, JUNIOR));
        repository.save(registration("TEAM-3", "Kappa Labs", COMPETITION, SENIOR));
        repository.save(registration("TEAM-4", "Beta Works", OTHER_COMPETITION, JUNIOR));
        TeamRegistration rejected = registration("TEAM-5", "Delta Bots", COMPETITION, JUNIOR)
                .resolveWith(new EligibilityVerdict(List.of(
                        new EligibilityViolation(EligibilityRuleCode.of("AGE"), "too young"))));
        repository.save(rejected);

        List<TeamRegistration> found = repository.findByCategory(COMPETITION, JUNIOR);

        assertEquals(List.of("Alpha Bots", "Delta Bots", "Omega Crew"),
                found.stream().map(TeamRegistration::name).toList());
        assertEquals(RegistrationStatus.REJECTED, found.get(1).status());
    }

    @Test
    void findsNoRegistrationsForACategoryWithoutTeams() {
        assertTrue(repository().findByCategory(COMPETITION, SENIOR).isEmpty());
    }

    private static TeamRegistration registration(String id, String name, CompetitionId competition,
            CategoryId category) {
        return new TeamRegistration(TeamId.of(id), competition, category, name, TeamFixtures.eligibleMembers(),
                TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments());
    }
}

package com.dps.roboleague.application.usecase;

import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.enrollment.team.RegistrationStatus;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.port.in.FindCategoryRounds;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RoundOrdinal;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TeamFixtures;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consultas de una categoría: sus equipos inscriptos y sus rondas programadas. */
class CategoryQueriesUseCaseTest {

    private final TestEdition edition = TestEdition.start();

    @Test
    void listsEveryTeamOfTheCategoryWithItsStatusOrderedByName() {
        TeamId omega = edition.registerEligibleTeam("Omega Crew");
        TeamId alpha = edition.registerEligibleTeam("Alpha Bots");
        TeamId rejected = edition.register("Delta Bots", TeamFixtures.eligibleMembers(), TeamFixtures.overweightRobot(),
                TeamFixtures.completeDocuments()).teamId();

        List<TeamRegistration> teams = teams(edition.competitionId(), edition.categoryId());

        assertEquals(List.of(alpha, rejected, omega), teams.stream().map(TeamRegistration::id).toList());
        assertEquals(List.of(RegistrationStatus.ACCEPTED, RegistrationStatus.REJECTED, RegistrationStatus.ACCEPTED),
                teams.stream().map(TeamRegistration::status).toList());
    }

    @Test
    void listsTheRoundsOfTheCategoryOrderedByOrdinal() {
        TeamId delta = edition.registerEligibleTeam("Delta Bots");
        edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 2, List.of(delta));
        edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1, List.of(delta));

        List<Round> rounds = rounds(edition.competitionId(), edition.categoryId());

        assertEquals(List.of(RoundOrdinal.of(1), RoundOrdinal.of(2)), rounds.stream().map(Round::ordinal).toList());
    }

    @Test
    void aCategoryWithoutTeamsOrRoundsHasEmptyLists() {
        assertTrue(teams(edition.competitionId(), edition.categoryId()).isEmpty());
        assertTrue(rounds(edition.competitionId(), edition.categoryId()).isEmpty());
    }

    @Test
    void anUnknownCompetitionOrCategoryIsNotFound() {
        CompetitionId unknownCompetition = CompetitionId.of("COMPETITION-404");
        CategoryId unknownCategory = CategoryId.of("CATEGORY-404");

        assertThrows(NotFoundException.class, () -> teams(unknownCompetition, edition.categoryId()));
        assertThrows(NotFoundException.class, () -> teams(edition.competitionId(), unknownCategory));
        assertThrows(NotFoundException.class, () -> rounds(unknownCompetition, edition.categoryId()));
        assertThrows(NotFoundException.class, () -> rounds(edition.competitionId(), unknownCategory));
    }

    private List<TeamRegistration> teams(CompetitionId competitionId, CategoryId categoryId) {
        return edition.module().findCategoryTeamsUseCase()
                .execute(new FindCategoryTeams.Command(competitionId, categoryId));
    }

    private List<Round> rounds(CompetitionId competitionId, CategoryId categoryId) {
        return edition.module().findCategoryRoundsUseCase()
                .execute(new FindCategoryRounds.Command(competitionId, categoryId));
    }
}

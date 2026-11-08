package com.dps.roboleague;

import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.port.in.AcceptAppeal;
import com.dps.roboleague.appeals.port.in.FindAppeal;
import com.dps.roboleague.appeals.port.in.RejectAppeal;
import com.dps.roboleague.appeals.port.in.SubmitAppeal;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.port.in.CreateSeason;
import com.dps.roboleague.enrollment.port.in.FindCategoryTeams;
import com.dps.roboleague.enrollment.port.in.FindCompetition;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistration;
import com.dps.roboleague.enrollment.port.in.RegisterTeam;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.port.in.FindRunResult;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.port.in.GetStandings;
import com.dps.roboleague.results.port.in.PublishStandings;
import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.results.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.RankingService;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.port.in.CreateCompetition;
import com.dps.roboleague.rules.port.in.PublishRulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.ScheduleConflictDetector;
import com.dps.roboleague.scheduling.port.in.FindCategoryRounds;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.scheduling.port.in.ScheduleRound;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.audit.AuditLog;
import com.dps.roboleague.shared.port.in.FindAuditTrail;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoboLeagueApplicationTest {

    private static final List<Class<?>> INPUT_PORTS = List.of(AcceptAppeal.class, CalculateRunScore.class,
            CaptureRunResult.class, CreateCompetition.class, CreateSeason.class, FindAppeal.class,
            FindAuditTrail.class, FindCategoryRounds.class, FindCategoryTeams.class, FindCompetition.class, FindRound.class, FindRunResult.class,
            FindTeamRegistration.class, GenerateStandings.class, GetStandings.class, PublishRulebook.class,
            PublishStandings.class, RecalculateStandings.class, RegisterAutomaticMeasurements.class,
            RegisterPanelEvaluations.class, RegisterTeam.class, RejectAppeal.class, ScheduleRound.class,
            SubmitAppeal.class);
    private static final List<Class<?>> SHARED_COMPONENTS = List.of(SeasonRepository.class,
            CompetitionRepository.class, RulebookRepository.class, TeamRegistrationRepository.class,
            RoundRepository.class, RunResultRepository.class, StandingsRepository.class, AppealRepository.class,
            AuditLog.class, IdGenerator.class, Clock.class, ScheduleConflictDetector.class, RankingService.class,
            CategoryScoringService.class);

    @Autowired ApplicationContext context;
    @LocalServerPort int port;

    @Test
    void startsWithExactlyOneBeanForEveryInputPortAndSharedAdapters() {
        INPUT_PORTS.forEach(portType -> assertEquals(1, context.getBeansOfType(portType).size(), portType.getName()));
        SHARED_COMPONENTS.forEach(componentType ->
                assertEquals(1, context.getBeansOfType(componentType).size(), componentType.getName()));
        SHARED_COMPONENTS.forEach(componentType ->
                assertSame(context.getBean(componentType), context.getBean(componentType), componentType.getName()));
    }

    @Test
    void servesTheTechnicalHealthCheckThroughRealHttp() {
        ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                .get().uri("/technical/health").retrieve().toEntity(String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"status\":\"UP\"}", response.getBody());
    }
}

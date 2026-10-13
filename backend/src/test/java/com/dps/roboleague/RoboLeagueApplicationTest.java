package com.dps.roboleague;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dps.roboleague.domain.port.in.*;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.ranking.CategoryScoringService;
import com.dps.roboleague.domain.ranking.RankingService;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.schedule.ScheduleConflictDetector;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.team.TeamRegistrationRepository;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoboLeagueApplicationTest {

    private static final List<Class<?>> INPUT_PORTS = List.of(AcceptAppeal.class, CalculateRunScore.class,
            CaptureRunResult.class, CreateCompetition.class, CreateSeason.class, FindAppeal.class,
            FindAuditTrail.class, FindCompetition.class, FindRound.class, FindRunResult.class,
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

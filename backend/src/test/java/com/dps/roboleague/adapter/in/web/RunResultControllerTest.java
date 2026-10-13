package com.dps.roboleague.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dps.roboleague.application.usecase.CaptureRunResultUseCase;
import com.dps.roboleague.application.usecase.FindRunResultUseCase;
import com.dps.roboleague.application.usecase.RegisterAutomaticMeasurementsUseCase;
import com.dps.roboleague.application.usecase.RegisterPanelEvaluationsUseCase;
import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Heat;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.shared.ArenaId;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.HeatId;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.infrastructure.id.SequentialIdGenerator;
import com.dps.roboleague.infrastructure.memory.InMemoryAuditLog;
import com.dps.roboleague.infrastructure.memory.InMemoryRoundRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRulebookRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryStandingsRepository;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.ShowcaseFixture;
import com.dps.roboleague.support.TeamFixtures;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RunResultControllerTest {

    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");
    private static final CompetitionId COMPETITION = CompetitionId.of("COMP-1");
    private static final CategoryId CATEGORY = CategoryId.of("CAT-1");

    private static final String COMPLETE_CAPTURE = """
            {
              "roundId":"%s", "teamId":"%s", "attemptNumber":1, "actor":"scorekeeper",
              "measurements":{"TIME":70,"OBJECTIVES":5,"ENERGY":40},
              "evaluations":[
                {"judgeId":"J1","criterion":"DESIGN","score":8},
                {"judgeId":"J2","criterion":"DESIGN","score":7}
              ],
              "incidents":[{"code":"RESTART","occurrences":1}]
            }
            """;
    private static final String AUTOMATIC_CAPTURE = """
            {
              "roundId":"%s", "teamId":"%s", "attemptNumber":1, "actor":"track-system",
              "measurements":{"TIME":70,"ACCURACY":0.8}
            }
            """;
    private static final String PANEL_CAPTURE = """
            {
              "roundId":"%s", "teamId":"%s", "attemptNumber":1, "actor":"head-judge",
              "evaluations":[
                {"judgeId":"J1","criterion":"CREATIVITY","score":8},
                {"judgeId":"J1","criterion":"EXECUTION","score":9},
                {"judgeId":"J2","criterion":"CREATIVITY","score":6},
                {"judgeId":"J2","criterion":"EXECUTION","score":7}
              ],
              "incidents":[{"code":"RESTART","occurrences":1}]
            }
            """;

    private MockMvc mvc;
    private RoundId ordinaryRound;
    private RoundId mixedRound;
    private TeamId team;

    @BeforeEach
    void setUp() {
        team = TeamId.of("TEAM-1");
        ordinaryRound = RoundId.of("ROUND-1");
        mixedRound = RoundId.of("ROUND-2");
        InMemoryRoundRepository rounds = new InMemoryRoundRepository();
        rounds.save(scheduledRound(ordinaryRound, HeatId.of("HEAT-1"), RescueEditionFixture.CHALLENGE_ID, 1));
        rounds.save(scheduledRound(mixedRound, HeatId.of("HEAT-2"), ShowcaseFixture.CHALLENGE_ID, 2));
        InMemoryRulebookRepository rulebooks = new InMemoryRulebookRepository();
        rulebooks.save(Rulebook.of(COMPETITION, RulebookVersion.first(), LocalDate.of(2026, 3, 1),
                RescueEditionFixture.rulebook(List.of(RescueEditionFixture.rescueChallenge(),
                        ShowcaseFixture.mixedShowcase()), RescueEditionFixture.attemptAggregation())));
        InMemoryRunResultRepository results = new InMemoryRunResultRepository();
        InMemoryStandingsRepository standings = new InMemoryStandingsRepository();
        SequentialIdGenerator ids = new SequentialIdGenerator();
        InMemoryAuditLog audit = new InMemoryAuditLog();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        RunResultController controller = new RunResultController(
                new CaptureRunResultUseCase(rounds, rulebooks, results, standings, ids, audit, clock),
                new RegisterAutomaticMeasurementsUseCase(rounds, rulebooks, results, standings, ids, audit, clock),
                new RegisterPanelEvaluationsUseCase(rounds, rulebooks, results, standings, ids, audit, clock),
                new FindRunResultUseCase(results));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private Round scheduledRound(RoundId roundId, HeatId heatId, ChallengeId challengeId, int ordinal) {
        TeamRegistration registration = new TeamRegistration(team, COMPETITION, CATEGORY, "Delta Bots",
                TeamFixtures.eligibleMembers(), TeamFixtures.eligibleRobot(), TeamFixtures.completeDocuments())
                .resolveWith(new EligibilityVerdict(List.of()));
        Round round = new Round(roundId, COMPETITION, CATEGORY, challengeId, RoundOrdinal.of(ordinal),
                RulebookVersion.first());
        Heat heat = new Heat(heatId, roundId, team, ArenaId.of("A1"),
                new TimeSlot(LocalDateTime.of(2026, 3, 2, 9 + ordinal, 0), Duration.ofMinutes(15)),
                Set.of(JudgeId.of("J1"), JudgeId.of("J2")));
        return round.schedule(heat, registration);
    }

    @Test
    void capturesAndFindsACompleteOrdinaryRun() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_CAPTURE.formatted(ordinaryRound.value(), team.value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("RUN-1"))
                .andExpect(jsonPath("$.completionStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.rulebookVersion").value(1))
                .andExpect(jsonPath("$.measurements.OBJECTIVES").value(5))
                .andExpect(jsonPath("$.evaluations[0].judgeId").value("J1"))
                .andExpect(jsonPath("$.incidents[0].code").value("RESTART"))
                .andExpect(jsonPath("$.correctionStatus").value("CAPTURED"))
                .andExpect(jsonPath("$.correctionCount").value(0));

        mvc.perform(get("/api/runs/RUN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundId").value(ordinaryRound.value()))
                .andExpect(jsonPath("$.heatId").value("HEAT-1"))
                .andExpect(jsonPath("$.teamId").value(team.value()))
                .andExpect(jsonPath("$.challengeId").value("RESCUE"));
    }

    @Test
    void automaticThenPanelCompletesTheSameRunAndRejectsADuplicateSource() throws Exception {
        mvc.perform(post("/api/runs/automatic-measurements").contentType(MediaType.APPLICATION_JSON)
                        .content(AUTOMATIC_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("RUN-1"))
                .andExpect(jsonPath("$.completionStatus").value("PENDING"))
                .andExpect(jsonPath("$.missingSources[0]").value("JUDGES"))
                .andExpect(jsonPath("$.sources[0].source").value("AUTOMATIC"))
                .andExpect(jsonPath("$.sources[0].received").value(true))
                .andExpect(jsonPath("$.sources[0].actor").value("track-system"));

        mvc.perform(post("/api/runs/panel-evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(PANEL_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("RUN-1"))
                .andExpect(jsonPath("$.completionStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.missingSources").isEmpty())
                .andExpect(jsonPath("$.sources[1].received").value(true))
                .andExpect(jsonPath("$.sources[1].actor").value("head-judge"));

        mvc.perform(post("/api/runs/automatic-measurements").contentType(MediaType.APPLICATION_JSON)
                        .content(AUTOMATIC_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already registered")));

        mvc.perform(get("/api/runs/RUN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].actor").value("track-system"));
    }

    @Test
    void panelThenAutomaticCompletesTheSameRun() throws Exception {
        mvc.perform(post("/api/runs/panel-evaluations").contentType(MediaType.APPLICATION_JSON)
                        .content(PANEL_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("RUN-1"))
                .andExpect(jsonPath("$.completionStatus").value("PENDING"))
                .andExpect(jsonPath("$.missingSources[0]").value("AUTOMATIC"));

        mvc.perform(post("/api/runs/automatic-measurements").contentType(MediaType.APPLICATION_JSON)
                        .content(AUTOMATIC_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("RUN-1"))
                .andExpect(jsonPath("$.completionStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.sources[0].receivedAt").value(NOW.toString()))
                .andExpect(jsonPath("$.sources[1].receivedAt").value(NOW.toString()));
    }

    @Test
    void mapsInvalidValuesBusinessRulesAndMissingRunsToTheSharedErrorFormat() throws Exception {
        mvc.perform(post("/api/runs/automatic-measurements").contentType(MediaType.APPLICATION_JSON)
                        .content(AUTOMATIC_CAPTURE.formatted(mixedRound.value(), team.value())
                                .replace("\"attemptNumber\":1", "\"attemptNumber\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE_CAPTURE.formatted(mixedRound.value(), team.value())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));

        mvc.perform(get("/api/runs/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("request body is malformed"));
    }
}

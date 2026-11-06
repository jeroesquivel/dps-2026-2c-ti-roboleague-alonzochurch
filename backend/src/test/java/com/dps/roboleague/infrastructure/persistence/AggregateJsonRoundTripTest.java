package com.dps.roboleague.infrastructure.persistence;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.enrollment.competition.Competition;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.competition.Season;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.team.TeamRegistration;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.rulebook.Rulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Todo lo que carga el escenario de demostración (temporada, competencia, reglamento, equipos, rondas, corridas de
 * las tres fuentes, tablas en sus revisiones y una apelación aceptada) se guarda como JSON y se lee de vuelta sin
 * perder nada: lo que vuelve a escribirse es el mismo documento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "roboleague.demo.enabled=true")
@Import(AggregateJsonRoundTripTest.FixedClockConfig.class)
class AggregateJsonRoundTripTest {

    private static final CompetitionId COMPETITION = CompetitionId.of("COMPETITION-1");
    private static final CategoryId CATEGORY = CategoryId.of("CATEGORY-1");

    private final AggregateJson json = new AggregateJson();

    @Autowired SeasonRepository seasons;
    @Autowired CompetitionRepository competitions;
    @Autowired RulebookRepository rulebooks;
    @Autowired TeamRegistrationRepository registrations;
    @Autowired RoundRepository rounds;
    @Autowired RunResultRepository runResults;
    @Autowired StandingsRepository standings;
    @Autowired AppealRepository appeals;

    @Test
    void everyAggregateOfTheDemoScenarioSurvivesBeingStoredAndRead() {
        List<Object> aggregates = new ArrayList<>(seasons.findAll());
        competitions.findById(COMPETITION).ifPresent(aggregates::add);
        for (int version = 1; version <= 4; version++) {
            rulebooks.find(COMPETITION, RulebookVersion.of(version)).ifPresent(aggregates::add);
        }
        for (int i = 1; i <= 6; i++) {
            registrations.findById(TeamId.of("TEAM-" + i)).ifPresent(aggregates::add);
        }
        for (int i = 1; i <= 10; i++) {
            rounds.findById(RoundId.of("ROUND-" + i)).ifPresent(aggregates::add);
        }
        for (int i = 1; i <= 60; i++) {
            runResults.findById(RunId.of("RUN-" + i)).ifPresent(aggregates::add);
        }
        aggregates.addAll(standings.findHistory(COMPETITION, CATEGORY));
        for (int i = 1; i <= 5; i++) {
            appeals.findById(AppealId.of("APPEAL-" + i)).ifPresent(aggregates::add);
        }

        assertFalse(aggregates.isEmpty());
        for (Class<?> type : List.of(Season.class, Competition.class, Rulebook.class, TeamRegistration.class,
                Round.class, RunResult.class, Standings.class, Appeal.class)) {
            assertTrue(aggregates.stream().anyMatch(type::isInstance), "the scenario has no " + type.getSimpleName());
        }
        for (Object aggregate : aggregates) {
            String stored = json.write(aggregate);
            Object read = json.read(stored, aggregate.getClass());
            assertEquals(canonical(parse(stored)), canonical(parse(json.write(read))),
                    "lost something storing a " + aggregate.getClass().getSimpleName());
        }
    }

    private JsonElement parse(String text) {
        return JsonParser.parseString(text);
    }

    /** Un mapa del dominio no tiene orden; sus entradas pueden volver en otro. Se las compara sin él. */
    private JsonElement canonical(JsonElement element) {
        if (element.isJsonArray()) {
            List<JsonElement> items = new ArrayList<>();
            element.getAsJsonArray().forEach(item -> items.add(canonical(item)));
            items.sort(Comparator.comparing(JsonElement::toString));
            JsonArray sorted = new JsonArray();
            items.forEach(sorted::add);
            return sorted;
        }
        if (element.isJsonObject()) {
            JsonObject copy = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(entry -> copy.add(entry.getKey(), canonical(entry.getValue())));
            return copy;
        }
        return element;
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-03-02T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}

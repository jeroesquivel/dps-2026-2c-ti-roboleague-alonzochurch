package com.dps.roboleague.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.adapter.in.web.standings.StandingsResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Verifica que el perfil {@code dev} carga los datos de application-dev.yaml. Es un contexto de Spring
 * aparte del resto de los tests, así que sus identificadores secuenciales empiezan de nuevo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevProfileApiTest {

    private static final String STANDINGS = "/competitions/COMPETITION-1/categories/CATEGORY-1/standings";

    @LocalServerPort int port;

    @Test
    void startsWithTheDemoTeamsAndRounds() {
        ResponseEntity<String> team = get("/teams/TEAM-1", String.class);
        assertEquals(HttpStatus.OK, team.getStatusCode());
        assertTrue(team.getBody().contains("Kappa Labs"), team.getBody());
        assertEquals(HttpStatus.OK, get("/rounds/ROUND-1", String.class).getStatusCode());
    }

    @Test
    void theSeededResultsProduceExplainedStandingsWithAPendingRun() {
        ResponseEntity<StandingsResponse> created = post(STANDINGS, "{\"actor\":\"organizer\"}");

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        StandingsResponse standings = created.getBody();
        assertEquals(List.of(1, 2, 3, 4),
                standings.entries().stream().map(StandingsResponse.EntryResponse::position).toList());
        assertEquals(List.of("Delta Bots", "Kappa Labs", "Omega Crew", "Sigma Works"),
                standings.entries().stream().map(StandingsResponse.EntryResponse::teamName).sorted().toList());
        assertEquals(1, standings.pendingRuns().size());
        assertEquals("Omega Crew", standings.pendingRuns().getFirst().teamName());
        assertEquals(List.of("JUDGES"), standings.pendingRuns().getFirst().missingSources());
        assertTrue(standings.entries().stream()
                .flatMap(entry -> entry.explanation().subtotals().stream())
                .flatMap(subtotal -> subtotal.rounds().stream())
                .anyMatch(round -> round.status().equals("DISCARDED")));
    }

    private <T> ResponseEntity<T> get(String path, Class<T> type) {
        return client().get().uri(path).retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(type);
    }

    private ResponseEntity<StandingsResponse> post(String path, String json) {
        return client().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json)
                .retrieve().onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(StandingsResponse.class);
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }
}

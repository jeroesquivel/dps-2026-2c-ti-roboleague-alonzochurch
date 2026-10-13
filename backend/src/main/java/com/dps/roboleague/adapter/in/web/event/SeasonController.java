package com.dps.roboleague.adapter.in.web.event;

import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.shared.SeasonId;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración del evento, primera parte: crear una temporada y, dentro de ella, una competencia
 * con sus categorías y su reglamento inicial. Sólo invoca los puertos {@code CreateSeason} y
 * {@code CreateCompetition}; las temporadas no tienen consulta propia, por eso su creación no
 * devuelve {@code Location}.
 */
@RestController
@RequestMapping("/seasons")
public final class SeasonController {

    private final CreateSeason createSeason;
    private final CreateCompetition createCompetition;

    public SeasonController(CreateSeason createSeason, CreateCompetition createCompetition) {
        this.createSeason = createSeason;
        this.createCompetition = createCompetition;
    }

    @PostMapping
    public ResponseEntity<SeasonCreatedResponse> createSeason(@RequestBody CreateSeasonRequest request) {
        SeasonId seasonId = createSeason.execute(EventWebMapper.toCommand(request));
        return ResponseEntity.status(201).body(EventWebMapper.toResponse(seasonId));
    }

    @PostMapping("/{seasonId}/competitions")
    public ResponseEntity<CompetitionCreatedResponse> createCompetition(@PathVariable("seasonId") String seasonId,
            @RequestBody CreateCompetitionRequest request) {
        CreateCompetition.Result result = createCompetition.execute(EventWebMapper.toCommand(seasonId, request));
        return ResponseEntity.created(URI.create("/competitions/" + result.competitionId().value()))
                .body(EventWebMapper.toResponse(result, request));
    }
}

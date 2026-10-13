package com.dps.roboleague.adapter.in.web.event;

import com.dps.roboleague.domain.port.in.FindCompetition;
import com.dps.roboleague.domain.port.in.PublishRulebook;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CompetitionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración del evento, segunda parte: consultar una competencia y publicar una nueva versión de
 * su reglamento. Las versiones históricas no se modifican: publicar sólo cambia la versión activa
 * para operaciones futuras.
 */
@RestController
@RequestMapping("/competitions/{competitionId}")
public final class CompetitionController {

    private final FindCompetition findCompetition;
    private final PublishRulebook publishRulebook;

    public CompetitionController(FindCompetition findCompetition, PublishRulebook publishRulebook) {
        this.findCompetition = findCompetition;
        this.publishRulebook = publishRulebook;
    }

    @GetMapping
    public CompetitionResponse find(@PathVariable("competitionId") String competitionId) {
        return EventWebMapper.toResponse(findCompetition.execute(CompetitionId.of(competitionId)));
    }

    @PostMapping("/rulebooks")
    public ResponseEntity<RulebookPublishedResponse> publish(@PathVariable("competitionId") String competitionId,
            @RequestBody PublishRulebookRequest request) {
        RulebookVersion version = publishRulebook.execute(EventWebMapper.toCommand(competitionId, request));
        return ResponseEntity.status(201)
                .body(EventWebMapper.toResponse(CompetitionId.of(competitionId), version));
    }
}

package com.dps.roboleague.adapter.in.web.run;

import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.FindRunResult;
import com.dps.roboleague.domain.shared.RunId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de una corrida y de su puntaje, necesaria para identificar el resultado que se apelará y
 * para preparar una corrección. Es de sólo lectura: las correcciones entran únicamente por
 * {@code AcceptAppeal}.
 */
@RestController
@RequestMapping("/runs/{runId}")
public final class RunController {

    private final FindRunResult findRunResult;
    private final CalculateRunScore calculateRunScore;

    public RunController(FindRunResult findRunResult, CalculateRunScore calculateRunScore) {
        this.findRunResult = findRunResult;
        this.calculateRunScore = calculateRunScore;
    }

    @GetMapping
    public RunResponse find(@PathVariable("runId") String runId) {
        return RunWebMapper.toResponse(findRunResult.execute(RunId.of(runId)));
    }

    @GetMapping("/score")
    public RunScoreResponse score(@PathVariable("runId") String runId) {
        return RunWebMapper.toResponse(calculateRunScore.execute(new CalculateRunScore.Command(RunId.of(runId))));
    }
}

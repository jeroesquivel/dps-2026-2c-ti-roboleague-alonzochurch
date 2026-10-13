package com.dps.roboleague.adapter.in.web.run;

import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.FindRunResult;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.shared.RunId;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Captura y consulta de corridas. Un desafío común se captura en una sola operación; uno mixto recibe
 * sus fuentes (mediciones automáticas y evaluaciones del panel) por separado, en cualquier orden, y la
 * primera crea una corrida pendiente que la segunda completa. La consulta sirve también para
 * identificar el resultado que se apelará. Las correcciones entran únicamente por {@code AcceptAppeal}.
 */
@RestController
@RequestMapping("/runs")
public final class RunController {

    private final CaptureRunResult captureRunResult;
    private final RegisterAutomaticMeasurements registerAutomatic;
    private final RegisterPanelEvaluations registerPanel;
    private final FindRunResult findRunResult;
    private final CalculateRunScore calculateRunScore;

    public RunController(CaptureRunResult captureRunResult, RegisterAutomaticMeasurements registerAutomatic,
            RegisterPanelEvaluations registerPanel, FindRunResult findRunResult,
            CalculateRunScore calculateRunScore) {
        this.captureRunResult = captureRunResult;
        this.registerAutomatic = registerAutomatic;
        this.registerPanel = registerPanel;
        this.findRunResult = findRunResult;
        this.calculateRunScore = calculateRunScore;
    }

    /** Captura completa de un desafío no mixto: siempre crea la corrida ({@code 201}). */
    @PostMapping
    public ResponseEntity<RunResponse> capture(@RequestBody CaptureRunRequest request) {
        RunId runId = captureRunResult.execute(RunWebMapper.toCommand(request));
        return created(findRunResult.execute(runId));
    }

    /**
     * Fuente automática de un desafío mixto. Responde {@code 201} si esta fuente abrió la corrida y
     * {@code 200} si completó una que ya existía; en ambos casos informa el mismo {@code runId}.
     */
    @PostMapping("/automatic-measurements")
    public ResponseEntity<RunResponse> registerAutomatic(@RequestBody AutomaticMeasurementsRequest request) {
        RunId runId = registerAutomatic.execute(RunWebMapper.toCommand(request));
        return source(findRunResult.execute(runId));
    }

    /** Fuente de jueces de un desafío mixto; mismas respuestas que la fuente automática. */
    @PostMapping("/panel-evaluations")
    public ResponseEntity<RunResponse> registerPanel(@RequestBody PanelEvaluationsRequest request) {
        RunId runId = registerPanel.execute(RunWebMapper.toCommand(request));
        return source(findRunResult.execute(runId));
    }

    @GetMapping("/{runId}")
    public RunResponse find(@PathVariable("runId") String runId) {
        return RunWebMapper.toResponse(findRunResult.execute(RunId.of(runId)));
    }

    /**
     * Puntaje explicado de la corrida. Una corrida incompleta responde {@code 200} con estado
     * {@code PENDING} y las fuentes que faltan, sin total: el estado de completitud lo informa
     * {@code FindRunResult} y el puntaje sólo lo calcula {@code CalculateRunScore}.
     */
    @GetMapping("/{runId}/score")
    public RunScoreResponse score(@PathVariable("runId") String runId) {
        RunResult run = findRunResult.execute(RunId.of(runId));
        if (!run.completion().isComplete()) {
            return RunWebMapper.toPendingResponse(run);
        }
        return RunWebMapper.toResponse(calculateRunScore.execute(new CalculateRunScore.Command(run.id())));
    }

    /** La fuente que deja una sola recepción es la que creó la corrida; la segunda la completa. */
    private static ResponseEntity<RunResponse> source(RunResult run) {
        return run.completion().receipts().size() == 1 ? created(run)
                : ResponseEntity.ok(RunWebMapper.toResponse(run));
    }

    private static ResponseEntity<RunResponse> created(RunResult run) {
        return ResponseEntity.created(URI.create("/runs/" + run.id().value()))
                .body(RunWebMapper.toResponse(run));
    }
}

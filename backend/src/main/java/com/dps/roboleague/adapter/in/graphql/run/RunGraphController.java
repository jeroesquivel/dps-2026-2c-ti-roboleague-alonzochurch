package com.dps.roboleague.adapter.in.graphql.run;

import com.dps.roboleague.adapter.in.graphql.type.RoundGraph;
import com.dps.roboleague.adapter.in.graphql.type.RoundGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.RunGraph;
import com.dps.roboleague.adapter.in.graphql.type.RunGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.ScoreGraph;
import com.dps.roboleague.adapter.in.graphql.type.ScoreGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.FindRunResult;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import java.util.concurrent.CompletableFuture;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public final class RunGraphController {

    private final FindRunResult findRun;
    private final FindRound findRound;
    private final CalculateRunScore calculateScore;

    public RunGraphController(FindRunResult findRun, FindRound findRound, CalculateRunScore calculateScore) {
        this.findRun = findRun;
        this.findRound = findRound;
        this.calculateScore = calculateScore;
    }

    @QueryMapping
    public RunGraph run(@Argument("id") String id) {
        return RunGraphMapper.toGraph(findRun.execute(RunId.of(id)));
    }

    @SchemaMapping(typeName = "Run", field = "team")
    public CompletableFuture<TeamGraph> team(RunGraph run, DataLoader<String, TeamGraph> teams) {
        return teams.load(run.teamId());
    }

    @SchemaMapping(typeName = "Run", field = "round")
    public RoundGraph round(RunGraph run) {
        return RoundGraphMapper.toGraph(findRound.execute(RoundId.of(run.roundId())));
    }

    @SchemaMapping(typeName = "Run", field = "score")
    public ScoreGraph score(RunGraph graph) {
        RunResult run = findRun.execute(RunId.of(graph.id()));
        return run.completion().isComplete()
                ? ScoreGraphMapper.complete(calculateScore.execute(new CalculateRunScore.Command(run.id())))
                : ScoreGraphMapper.pending(run);
    }
}

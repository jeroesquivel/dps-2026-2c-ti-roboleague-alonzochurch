package com.dps.roboleague.adapter.in.graphql.round;

import com.dps.roboleague.adapter.in.graphql.type.RoundGraph;
import com.dps.roboleague.adapter.in.graphql.type.RoundGraphMapper;
import com.dps.roboleague.adapter.in.graphql.type.RunGraph;
import com.dps.roboleague.adapter.in.graphql.type.RunGraphMapper;
import com.dps.roboleague.results.port.in.FindRoundRuns;
import com.dps.roboleague.scheduling.port.in.FindRound;
import com.dps.roboleague.shared.RoundId;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public final class RoundGraphController {

    private final FindRound findRound;
    private final FindRoundRuns findRuns;

    public RoundGraphController(FindRound findRound, FindRoundRuns findRuns) {
        this.findRound = findRound;
        this.findRuns = findRuns;
    }

    @QueryMapping
    public RoundGraph round(@Argument("id") String id) {
        return RoundGraphMapper.toGraph(findRound.execute(RoundId.of(id)));
    }

    @SchemaMapping(typeName = "Round", field = "runs")
    public List<RunGraph> runs(RoundGraph round) {
        return findRuns.execute(new FindRoundRuns.Command(RoundId.of(round.id()))).stream()
                .map(RunGraphMapper::toGraph).toList();
    }
}

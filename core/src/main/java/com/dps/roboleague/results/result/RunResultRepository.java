package com.dps.roboleague.results.result;

import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import java.util.List;
import java.util.Optional;

public interface RunResultRepository {

    void save(RunResult result);

    Optional<RunResult> findById(RunId id);

    List<RunResult> findByRound(RoundId roundId);

    List<RunResult> findByTeam(TeamId teamId);
}

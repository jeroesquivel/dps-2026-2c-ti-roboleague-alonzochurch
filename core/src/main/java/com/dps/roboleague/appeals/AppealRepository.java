package com.dps.roboleague.appeals;

import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.RunId;
import java.util.List;
import java.util.Optional;

public interface AppealRepository {

    void save(Appeal appeal);

    Optional<Appeal> findById(AppealId id);

    List<Appeal> findByRun(RunId runId);
}

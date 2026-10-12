package com.dps.roboleague.domain.appeal;

import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.RunId;
import java.util.List;
import java.util.Optional;

public interface AppealRepository {

    void save(Appeal appeal);

    Optional<Appeal> findById(AppealId id);

    List<Appeal> findByRun(RunId runId);
}

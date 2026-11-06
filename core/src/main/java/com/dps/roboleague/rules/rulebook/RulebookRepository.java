package com.dps.roboleague.rules.rulebook;

import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RulebookVersion;
import java.util.Optional;

public interface RulebookRepository {

    void save(Rulebook rulebook);

    Optional<Rulebook> find(CompetitionId competitionId, RulebookVersion version);
}

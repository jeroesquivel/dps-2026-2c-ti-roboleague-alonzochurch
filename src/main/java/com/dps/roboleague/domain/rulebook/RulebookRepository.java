package com.dps.roboleague.domain.rulebook;

import com.dps.roboleague.domain.shared.CompetitionId;
import java.util.Optional;

public interface RulebookRepository {

    void save(Rulebook rulebook);

    Optional<Rulebook> find(CompetitionId competitionId, RulebookVersion version);
}

package com.dps.roboleague.infrastructure.persistence.jpa;

import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.rules.rulebook.Rulebook;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.RulebookVersion;
import java.util.Optional;

public final class JpaRulebookRepository implements RulebookRepository {

    private final RulebookRows rows;
    private final AggregateJson json;

    public JpaRulebookRepository(RulebookRows rows, AggregateJson json) {
        this.rows = rows;
        this.json = json;
    }

    @Override
    public void save(Rulebook rulebook) {
        String competition = rulebook.competitionId().value();
        int version = rulebook.version().number();
        rows.save(new RulebookRow(Rows.rulebookKey(competition, version), competition, version,
                json.write(rulebook)));
    }

    @Override
    public Optional<Rulebook> find(CompetitionId competitionId, RulebookVersion version) {
        return rows.findById(Rows.rulebookKey(competitionId.value(), version.number()))
                .map(row -> json.read(row.payload(), Rulebook.class));
    }
}

package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.ranking.StandingsRepositoryContractTest;

class InMemoryStandingsRepositoryTest extends StandingsRepositoryContractTest {

    private final StandingsRepository repository = new InMemoryStandingsRepository();

    @Override
    protected StandingsRepository repository() {
        return repository;
    }
}

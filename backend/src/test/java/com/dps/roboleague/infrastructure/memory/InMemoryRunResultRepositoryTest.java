package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.results.result.RunResultRepositoryContractTest;

class InMemoryRunResultRepositoryTest extends RunResultRepositoryContractTest {

    private final RunResultRepository repository = new InMemoryRunResultRepository();

    @Override
    protected RunResultRepository repository() {
        return repository;
    }
}

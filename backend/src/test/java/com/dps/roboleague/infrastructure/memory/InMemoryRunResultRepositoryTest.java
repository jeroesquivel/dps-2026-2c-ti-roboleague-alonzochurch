package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.result.RunResultRepositoryContractTest;

class InMemoryRunResultRepositoryTest extends RunResultRepositoryContractTest {

    private final RunResultRepository repository = new InMemoryRunResultRepository();

    @Override
    protected RunResultRepository repository() {
        return repository;
    }
}

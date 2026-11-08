package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepositoryContractTest;

class InMemoryTeamRegistrationRepositoryTest extends TeamRegistrationRepositoryContractTest {

    private final TeamRegistrationRepository repository = new InMemoryTeamRegistrationRepository();

    @Override
    protected TeamRegistrationRepository repository() {
        return repository;
    }
}

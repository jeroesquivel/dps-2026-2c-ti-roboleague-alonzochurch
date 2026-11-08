package com.dps.roboleague.infrastructure.persistence;

import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepositoryContractTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** El mismo contrato que cumple la versión en memoria, ahora contra la base de datos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "roboleague.persistence=jpa", "spring.datasource.url=jdbc:h2:mem:contracts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class JpaTeamRegistrationRepositoryTest extends TeamRegistrationRepositoryContractTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired TeamRegistrationRepository repository;

    @BeforeEach
    @AfterEach
    void emptyTheTable() {
        jdbc.update("DELETE FROM team_registrations");
    }

    @Override
    protected TeamRegistrationRepository repository() {
        return repository;
    }
}

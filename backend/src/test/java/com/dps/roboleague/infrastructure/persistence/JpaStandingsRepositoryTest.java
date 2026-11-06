package com.dps.roboleague.infrastructure.persistence;

import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.ranking.StandingsRepositoryContractTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

/** El mismo contrato que cumple la versión en memoria, ahora contra la base de datos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "roboleague.persistence=jpa", "spring.datasource.url=jdbc:h2:mem:contracts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class JpaStandingsRepositoryTest extends StandingsRepositoryContractTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired StandingsRepository repository;

    @BeforeEach
    @AfterEach
    void emptyTheTable() {
        jdbc.update("DELETE FROM standings");
    }

    @Override
    protected StandingsRepository repository() {
        return repository;
    }
}

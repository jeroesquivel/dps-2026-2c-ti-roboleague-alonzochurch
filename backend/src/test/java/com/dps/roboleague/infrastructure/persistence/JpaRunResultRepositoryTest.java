package com.dps.roboleague.infrastructure.persistence;

import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.results.result.RunResultRepositoryContractTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

/** El mismo contrato que cumple la versión en memoria, ahora contra la base de datos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "roboleague.persistence=jpa", "spring.datasource.url=jdbc:h2:mem:contracts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class JpaRunResultRepositoryTest extends RunResultRepositoryContractTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired RunResultRepository repository;

    @BeforeEach
    @AfterEach
    void emptyTheTable() {
        jdbc.update("DELETE FROM run_results");
    }

    @Override
    protected RunResultRepository repository() {
        return repository;
    }
}

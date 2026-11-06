package com.dps.roboleague.demo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * El escenario de demostración completo, con las mismas verificaciones que en memoria, corriendo sobre la base de
 * datos: los casos de uso son los mismos, solo cambian los repositorios y que cada caso de uso es una transacción.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "roboleague.demo.enabled=true", "roboleague.persistence=jpa",
        "spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(DemoScenarioTest.FixedClockConfig.class)
class JpaDemoScenarioTest extends DemoScenarioTest {
}

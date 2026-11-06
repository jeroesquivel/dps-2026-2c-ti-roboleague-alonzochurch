package com.dps.roboleague.config;

import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.enrollment.competition.CompetitionRepository;
import com.dps.roboleague.enrollment.competition.SeasonRepository;
import com.dps.roboleague.enrollment.team.TeamRegistrationRepository;
import com.dps.roboleague.infrastructure.persistence.AggregateJson;
import com.dps.roboleague.infrastructure.persistence.JdbcIdGenerator;
import com.dps.roboleague.infrastructure.persistence.TransactionalUseCases;
import com.dps.roboleague.infrastructure.persistence.jpa.AppealRows;
import com.dps.roboleague.infrastructure.persistence.jpa.AuditEventRows;
import com.dps.roboleague.infrastructure.persistence.jpa.CompetitionRows;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaAppealRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaAuditLog;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaCompetitionRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaRoundRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaRulebookRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaRunResultRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaSeasonRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaStandingsRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.JpaTeamRegistrationRepository;
import com.dps.roboleague.infrastructure.persistence.jpa.RoundRows;
import com.dps.roboleague.infrastructure.persistence.jpa.RulebookRows;
import com.dps.roboleague.infrastructure.persistence.jpa.RunResultRows;
import com.dps.roboleague.infrastructure.persistence.jpa.SeasonRows;
import com.dps.roboleague.infrastructure.persistence.jpa.StandingsRows;
import com.dps.roboleague.infrastructure.persistence.jpa.TeamRegistrationRows;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.audit.AuditLog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Persistencia real: los repositorios del dominio implementados sobre la base de datos, con JPA. Se activa con
 * {@code roboleague.persistence=jpa}; el perfil {@code postgres} apunta a PostgreSQL y lo activa.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "roboleague.persistence", havingValue = "jpa")
public class JpaPersistenceConfig {

    @Bean AggregateJson aggregateJson() { return new AggregateJson(); }

    @Bean static TransactionalUseCases transactionalUseCases(ObjectProvider<PlatformTransactionManager> tx) {
        return new TransactionalUseCases(tx);
    }

    @Bean SeasonRepository seasonRepository(SeasonRows rows, AggregateJson json) {
        return new JpaSeasonRepository(rows, json);
    }
    @Bean CompetitionRepository competitionRepository(CompetitionRows rows, AggregateJson json) {
        return new JpaCompetitionRepository(rows, json);
    }
    @Bean RulebookRepository rulebookRepository(RulebookRows rows, AggregateJson json) {
        return new JpaRulebookRepository(rows, json);
    }
    @Bean TeamRegistrationRepository teamRegistrationRepository(TeamRegistrationRows rows, AggregateJson json) {
        return new JpaTeamRegistrationRepository(rows, json);
    }
    @Bean RoundRepository roundRepository(RoundRows rows, AggregateJson json) {
        return new JpaRoundRepository(rows, json);
    }
    @Bean RunResultRepository runResultRepository(RunResultRows rows, AggregateJson json) {
        return new JpaRunResultRepository(rows, json);
    }
    @Bean StandingsRepository standingsRepository(StandingsRows rows, AggregateJson json) {
        return new JpaStandingsRepository(rows, json);
    }
    @Bean AppealRepository appealRepository(AppealRows rows, AggregateJson json) {
        return new JpaAppealRepository(rows, json);
    }
    @Bean AuditLog auditLog(AuditEventRows rows, AggregateJson json) {
        return new JpaAuditLog(rows, json);
    }
    @Bean IdGenerator idGenerator(JdbcTemplate jdbc) {
        return new JdbcIdGenerator(jdbc);
    }
}

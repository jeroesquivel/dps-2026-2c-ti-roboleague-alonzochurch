package com.dps.roboleague.config;

import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.competition.CompetitionRepository;
import com.dps.roboleague.domain.competition.SeasonRepository;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.team.TeamRegistrationRepository;
import com.dps.roboleague.infrastructure.id.SequentialIdGenerator;
import com.dps.roboleague.infrastructure.memory.InMemoryAppealRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryAuditLog;
import com.dps.roboleague.infrastructure.memory.InMemoryCompetitionRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRoundRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRulebookRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryRunResultRepository;
import com.dps.roboleague.infrastructure.memory.InMemorySeasonRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryStandingsRepository;
import com.dps.roboleague.infrastructure.memory.InMemoryTeamRegistrationRepository;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InfrastructureConfig {

    @Bean SeasonRepository seasonRepository() { return new InMemorySeasonRepository(); }
    @Bean CompetitionRepository competitionRepository() { return new InMemoryCompetitionRepository(); }
    @Bean RulebookRepository rulebookRepository() { return new InMemoryRulebookRepository(); }
    @Bean TeamRegistrationRepository teamRegistrationRepository() { return new InMemoryTeamRegistrationRepository(); }
    @Bean RoundRepository roundRepository() { return new InMemoryRoundRepository(); }
    @Bean RunResultRepository runResultRepository() { return new InMemoryRunResultRepository(); }
    @Bean StandingsRepository standingsRepository() { return new InMemoryStandingsRepository(); }
    @Bean AppealRepository appealRepository() { return new InMemoryAppealRepository(); }
    @Bean AuditLog auditLog() { return new InMemoryAuditLog(); }
    @Bean IdGenerator idGenerator() { return new SequentialIdGenerator(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}

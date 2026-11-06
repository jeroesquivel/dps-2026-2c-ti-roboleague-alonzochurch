package com.dps.roboleague.config;

import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.ranking.RankingService;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.scheduling.ScheduleConflictDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DomainServicesConfig {

    @Bean ScheduleConflictDetector scheduleConflictDetector() { return new ScheduleConflictDetector(); }
    @Bean RankingService rankingService() { return new RankingService(); }

    @Bean
    CategoryScoringService categoryScoringService(RoundRepository rounds, RunResultRepository results,
            RulebookRepository rulebooks, RankingService ranking) {
        return new CategoryScoringService(rounds, results, rulebooks, ranking);
    }
}

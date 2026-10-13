package com.dps.roboleague.demo;

import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Precarga datos de demostración al arrancar con el perfil {@code dev}; no existe en ningún otro perfil. */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
@EnableConfigurationProperties(DevSeedProperties.class)
class DevSeedConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DevSeedConfiguration.class);

    @Bean
    ApplicationRunner devDataSeeder(DevSeedProperties properties, CreateSeason createSeason,
            CreateCompetition createCompetition, RegisterTeam registerTeam, ScheduleRound scheduleRound,
            CaptureRunResult captureRunResult, RegisterAutomaticMeasurements registerMeasurements,
            RegisterPanelEvaluations registerPanel) {
        DevDataSeeder seeder = new DevDataSeeder(properties, createSeason, createCompetition, registerTeam,
                scheduleRound, captureRunResult, registerMeasurements, registerPanel);
        return arguments -> LOG.info(seeder.seed().describe());
    }
}

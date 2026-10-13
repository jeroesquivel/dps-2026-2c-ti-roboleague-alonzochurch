package com.dps.roboleague.config;

import com.dps.roboleague.demo.DemoScenario;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.CaptureRunResult;
import com.dps.roboleague.domain.port.in.CreateCompetition;
import com.dps.roboleague.domain.port.in.CreateSeason;
import com.dps.roboleague.domain.port.in.FindRunResult;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.GetStandings;
import com.dps.roboleague.domain.port.in.PublishStandings;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.port.in.RegisterAutomaticMeasurements;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "roboleague.demo.enabled", havingValue = "true")
public class DemoConfig {

    @Bean
    DemoScenario demoScenario(CreateSeason createSeason, CreateCompetition createCompetition, RegisterTeam registerTeam,
            ScheduleRound scheduleRound, CaptureRunResult captureRunResult,
            RegisterAutomaticMeasurements registerAutomaticMeasurements,
            RegisterPanelEvaluations registerPanelEvaluations, CalculateRunScore calculateRunScore,
            FindRunResult findRunResult, GenerateStandings generateStandings, PublishStandings publishStandings,
            RecalculateStandings recalculateStandings, SubmitAppeal submitAppeal, AcceptAppeal acceptAppeal,
            GetStandings getStandings) {
        return new DemoScenario(createSeason, createCompetition, registerTeam, scheduleRound, captureRunResult,
                registerAutomaticMeasurements, registerPanelEvaluations, calculateRunScore, findRunResult,
                generateStandings, publishStandings, recalculateStandings, submitAppeal, acceptAppeal, getStandings);
    }

    @Bean
    ApplicationRunner runDemo(DemoScenario scenario) {
        return arguments -> scenario.run();
    }
}

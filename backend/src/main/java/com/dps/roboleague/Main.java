package com.dps.roboleague;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class Main {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(RoboLeagueApplication.class)
                .web(WebApplicationType.NONE)
                .properties("roboleague.demo.enabled=true")
                .run(args)) {
            // DemoConfig executes the scenario through an ApplicationRunner.
        }
    }
}

package com.dps.roboleague.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Datos de demostración del perfil {@code dev}, tal como se declaran en {@code application-dev.yaml}.
 * Los nombres de métrica, criterio y penalización se escriben en el YAML sin distinguir mayúsculas.
 */
@ConfigurationProperties("roboleague.dev-seed")
public record DevSeedProperties(String actor, Season season, Competition competition, Category category,
        List<String> teams, String arena, List<String> judges, int heatMinutes, int heatSpacingMinutes,
        List<Round> rounds) {

    public record Season(String name, int year, LocalDate start, LocalDate end) {
    }

    public record Competition(String name, LocalDate start, LocalDate end) {
    }

    public record Category(String name, int minAge, int maxAge, String robotClass) {
    }

    public record Round(int ordinal, String challenge, LocalDateTime start, List<Result> results) {
    }

    /** Cómo llega el resultado de un equipo en una ronda. */
    public enum Source {
        CAPTURE,
        MEASUREMENTS,
        PANEL
    }

    public record Result(String team, @DefaultValue("CAPTURE") Source source, Map<String, String> measurements,
            Map<String, List<Integer>> judgeScores, List<String> incidents) {

        public Result {
            measurements = measurements == null ? Map.of() : Map.copyOf(measurements);
            judgeScores = judgeScores == null ? Map.of() : Map.copyOf(judgeScores);
            incidents = incidents == null ? List.of() : List.copyOf(incidents);
        }
    }
}

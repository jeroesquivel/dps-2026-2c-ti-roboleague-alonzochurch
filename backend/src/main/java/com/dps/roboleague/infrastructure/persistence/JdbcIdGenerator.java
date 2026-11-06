package com.dps.roboleague.infrastructure.persistence;

import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.CategoryId;
import com.dps.roboleague.shared.CompetitionId;
import com.dps.roboleague.shared.HeatId;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.MemberId;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.SeasonId;
import com.dps.roboleague.shared.TeamId;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Genera los mismos identificadores que la versión en memoria (TEAM-1, TEAM-2…), pero con el contador guardado en
 * la base, así que no se repiten después de reiniciar. Corre dentro de la transacción del caso de uso: el
 * incremento toma el lock de la fila, de modo que dos pedidos simultáneos no obtienen el mismo número.
 */
public final class JdbcIdGenerator implements IdGenerator {

    private final JdbcTemplate jdbc;

    public JdbcIdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SeasonId nextSeasonId() {
        return SeasonId.of(next("SEASON"));
    }

    @Override
    public CompetitionId nextCompetitionId() {
        return CompetitionId.of(next("COMPETITION"));
    }

    @Override
    public CategoryId nextCategoryId() {
        return CategoryId.of(next("CATEGORY"));
    }

    @Override
    public TeamId nextTeamId() {
        return TeamId.of(next("TEAM"));
    }

    @Override
    public RoundId nextRoundId() {
        return RoundId.of(next("ROUND"));
    }

    @Override
    public HeatId nextHeatId() {
        return HeatId.of(next("HEAT"));
    }

    @Override
    public RunId nextRunId() {
        return RunId.of(next("RUN"));
    }

    @Override
    public AppealId nextAppealId() {
        return AppealId.of(next("APPEAL"));
    }

    @Override
    public MemberId nextMemberId() {
        return MemberId.of(next("MEMBER"));
    }

    private String next(String prefix) {
        jdbc.update("UPDATE id_counter SET last_number = last_number + 1 WHERE prefix = ?", prefix);
        Long value = jdbc.queryForObject("SELECT last_number FROM id_counter WHERE prefix = ?", Long.class, prefix);
        return prefix + "-" + value;
    }
}

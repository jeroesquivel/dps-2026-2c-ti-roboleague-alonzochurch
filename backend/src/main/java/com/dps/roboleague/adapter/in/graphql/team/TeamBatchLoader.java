package com.dps.roboleague.adapter.in.graphql.team;

import com.dps.roboleague.adapter.in.graphql.type.TeamGraph;
import com.dps.roboleague.adapter.in.graphql.type.TeamGraphMapper;
import com.dps.roboleague.enrollment.port.in.FindTeamRegistrations;
import com.dps.roboleague.shared.TeamId;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Carga por lotes de los equipos de una consulta GraphQL: resuelve el N+1 de las filas de una tabla y de los turnos
 * de una ronda. En cada nivel de la consulta, los resolvers piden los equipos que necesitan y el {@code DataLoader}
 * los junta en una sola llamada al puerto {@link FindTeamRegistrations} (y en JPA, en un solo
 * {@code SELECT ... WHERE id IN (...)}).
 *
 * <p>La caché es por petición: un equipo que reaparece en otro nivel de la misma consulta no se vuelve a pedir, y la
 * caché se descarta al terminar la petición, así que nunca sirve datos de otra consulta.
 *
 * <p>Spring for GraphQL sólo acepta el lote como un {@code Mono} de Reactor. Es la única línea reactiva del
 * adaptador: envuelve una llamada síncrona al puerto.
 */
@Component
public final class TeamBatchLoader {

    public TeamBatchLoader(BatchLoaderRegistry registry, FindTeamRegistrations findTeamRegistrations) {
        registry.forTypePair(String.class, TeamGraph.class)
                .withOptions(options -> options.setCachingEnabled(true))
                .registerMappedBatchLoader((teamIds, environment) ->
                        Mono.fromSupplier(() -> load(findTeamRegistrations, teamIds)));
    }

    private static Map<String, TeamGraph> load(FindTeamRegistrations findTeamRegistrations, Set<String> teamIds) {
        Set<TeamId> ids = teamIds.stream().map(TeamId::of).collect(Collectors.toUnmodifiableSet());
        return findTeamRegistrations.execute(new FindTeamRegistrations.Command(ids)).values().stream()
                .collect(Collectors.toUnmodifiableMap(registration -> registration.id().value(),
                        TeamGraphMapper::toGraph));
    }
}

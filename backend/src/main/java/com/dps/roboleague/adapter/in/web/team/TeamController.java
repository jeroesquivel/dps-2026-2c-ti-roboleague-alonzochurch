package com.dps.roboleague.adapter.in.web.team;

import com.dps.roboleague.domain.port.in.FindTeamRegistration;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.shared.TeamId;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teams")
public final class TeamController {

    private final RegisterTeam registerTeam;
    private final FindTeamRegistration findTeamRegistration;

    public TeamController(RegisterTeam registerTeam, FindTeamRegistration findTeamRegistration) {
        this.registerTeam = registerTeam;
        this.findTeamRegistration = findTeamRegistration;
    }

    @PostMapping
    public ResponseEntity<RegistrationOutcomeResponse> register(@RequestBody RegisterTeamRequest request) {
        RegisterTeam.Outcome outcome = registerTeam.execute(TeamWebMapper.toCommand(request));
        return ResponseEntity.created(URI.create("/teams/" + outcome.teamId().value()))
                .body(TeamWebMapper.toResponse(outcome));
    }

    @GetMapping("/{teamId}")
    public TeamRegistrationResponse find(@PathVariable("teamId") String teamId) {
        return TeamWebMapper.toResponse(findTeamRegistration.execute(TeamId.of(teamId)));
    }
}

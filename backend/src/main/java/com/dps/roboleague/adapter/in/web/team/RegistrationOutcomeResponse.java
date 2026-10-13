package com.dps.roboleague.adapter.in.web.team;

import java.util.List;

public record RegistrationOutcomeResponse(String teamId, String status, List<ViolationResponse> violations) {
}

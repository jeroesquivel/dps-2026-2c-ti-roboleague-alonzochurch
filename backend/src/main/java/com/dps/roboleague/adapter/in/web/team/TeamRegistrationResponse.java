package com.dps.roboleague.adapter.in.web.team;

import java.math.BigDecimal;
import java.util.List;

public record TeamRegistrationResponse(String teamId, String competitionId, String categoryId, String name,
        String status, List<MemberResponse> members, RobotResponse robot, List<String> documentTypes,
        List<ViolationResponse> violations) {

    public record MemberResponse(String memberId, String fullName, String birthDate, String role) {
    }

    public record RobotResponse(String name, String robotClass, BigDecimal weightKg,
            DimensionsResponse dimensions) {
    }

    public record DimensionsResponse(int lengthMm, int widthMm, int heightMm) {
    }
}

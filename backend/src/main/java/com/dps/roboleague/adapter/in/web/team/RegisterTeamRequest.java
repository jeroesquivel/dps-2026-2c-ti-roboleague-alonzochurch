package com.dps.roboleague.adapter.in.web.team;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrato de entrada de la inscripción. Los campos son opcionales a nivel de formato: la presencia
 * y las invariantes se verifican al traducirlos a tipos del núcleo, que responde con un error de
 * entrada en lugar de una falla interna.
 */
public record RegisterTeamRequest(String competitionId, String categoryId, String teamName,
        List<MemberRequest> members, RobotRequest robot, List<DocumentRequest> documents, String actor) {

    public record MemberRequest(String fullName, String birthDate, String role) {
    }

    public record RobotRequest(String name, String robotClass, BigDecimal weightKg, DimensionsRequest dimensions) {
    }

    public record DimensionsRequest(Integer lengthMm, Integer widthMm, Integer heightMm) {
    }

    public record DocumentRequest(String type, String reference) {
    }
}

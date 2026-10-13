package com.dps.roboleague.adapter.in.web.team;

import com.dps.roboleague.domain.competition.RobotClass;
import com.dps.roboleague.domain.eligibility.EligibilityViolation;
import com.dps.roboleague.domain.port.in.RegisterTeam;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.team.Dimensions;
import com.dps.roboleague.domain.team.DocumentType;
import com.dps.roboleague.domain.team.Member;
import com.dps.roboleague.domain.team.MemberRole;
import com.dps.roboleague.domain.team.Robot;
import com.dps.roboleague.domain.team.TeamDocument;
import com.dps.roboleague.domain.team.TeamRegistration;
import com.dps.roboleague.domain.team.Weight;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Traduce entre los DTOs web y los tipos del núcleo. No decide reglas: sólo verifica presencia y
 * formato para no filtrar valores nulos hacia constructores del dominio.
 */
final class TeamWebMapper {

    private TeamWebMapper() {
    }

    static RegisterTeam.Command toCommand(RegisterTeamRequest request) {
        return new RegisterTeam.Command(CompetitionId.of(request.competitionId()),
                CategoryId.of(request.categoryId()), request.teamName(),
                required(request.members(), "members").stream().map(TeamWebMapper::memberOf).toList(),
                robotOf(required(request.robot(), "robot")), documentsOf(request.documents()),
                Actor.of(request.actor()));
    }

    static RegistrationOutcomeResponse toResponse(RegisterTeam.Outcome outcome) {
        return new RegistrationOutcomeResponse(outcome.teamId().value(), outcome.status().name(),
                violationsOf(outcome.verdict().violations()));
    }

    static TeamRegistrationResponse toResponse(TeamRegistration registration) {
        Robot robot = registration.robot();
        return new TeamRegistrationResponse(registration.id().value(), registration.competitionId().value(),
                registration.categoryId().value(), registration.name(), registration.status().name(),
                registration.members().members().stream().map(TeamWebMapper::memberResponseOf).toList(),
                new TeamRegistrationResponse.RobotResponse(robot.name(), robot.robotClass().code(),
                        robot.weight().kilograms(),
                        new TeamRegistrationResponse.DimensionsResponse(robot.dimensions().lengthMm(),
                                robot.dimensions().widthMm(), robot.dimensions().heightMm())),
                registration.documentTypes().stream().map(DocumentType::name).sorted().toList(),
                violationsOf(registration.rejectionReasons()));
    }

    private static RegisterTeam.MemberDraft memberOf(RegisterTeamRequest.MemberRequest request) {
        RegisterTeamRequest.MemberRequest member = required(request, "member");
        return new RegisterTeam.MemberDraft(member.fullName(), dateOf(member.birthDate()),
                enumOf(MemberRole.class, member.role(), "member role"));
    }

    private static Robot robotOf(RegisterTeamRequest.RobotRequest robot) {
        RegisterTeamRequest.DimensionsRequest dimensions = required(robot.dimensions(), "robot dimensions");
        return new Robot(robot.name(), RobotClass.of(robot.robotClass()),
                new Weight(required(robot.weightKg(), "robot weight")),
                new Dimensions(required(dimensions.lengthMm(), "robot length"),
                        required(dimensions.widthMm(), "robot width"),
                        required(dimensions.heightMm(), "robot height")));
    }

    private static List<TeamDocument> documentsOf(List<RegisterTeamRequest.DocumentRequest> documents) {
        if (documents == null) {
            return List.of();
        }
        return documents.stream()
                .map(document -> required(document, "document"))
                .map(document -> new TeamDocument(enumOf(DocumentType.class, document.type(), "document type"),
                        document.reference()))
                .toList();
    }

    private static TeamRegistrationResponse.MemberResponse memberResponseOf(Member member) {
        return new TeamRegistrationResponse.MemberResponse(member.id().value(), member.fullName(),
                member.birthDate().toString(), member.role().name());
    }

    private static List<ViolationResponse> violationsOf(List<EligibilityViolation> violations) {
        return violations.stream()
                .map(violation -> new ViolationResponse(violation.ruleCode().value(), violation.reason()))
                .toList();
    }

    private static LocalDate dateOf(String value) {
        try {
            return LocalDate.parse(required(value, "member birth date"));
        } catch (DateTimeParseException exception) {
            throw new InvalidValueException("birth date " + value + " is not an ISO date (yyyy-MM-dd)");
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String field) {
        String text = required(value, field).trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw new InvalidValueException(field + " must be one of " + Arrays.toString(type.getEnumConstants())
                    + " and was " + value);
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " is required");
        }
        return value;
    }
}

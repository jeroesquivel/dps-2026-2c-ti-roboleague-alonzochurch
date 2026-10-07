package com.dps.roboleague.domain.team;

import com.dps.roboleague.domain.eligibility.EligibilityVerdict;
import com.dps.roboleague.domain.eligibility.EligibilityViolation;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TeamRegistration {

    private final TeamId id;
    private final CompetitionId competitionId;
    private final CategoryId categoryId;
    private final String name;
    private final TeamMembers members;
    private final Robot robot;
    private final Map<DocumentType, TeamDocument> documents = new EnumMap<>(DocumentType.class);
    private RegistrationStatus status = RegistrationStatus.SUBMITTED;
    private List<EligibilityViolation> rejectionReasons = List.of();

    public TeamRegistration(TeamId id, CompetitionId competitionId, CategoryId categoryId, String name,
            TeamMembers members, Robot robot, Collection<TeamDocument> documents) {
        this.id = Objects.requireNonNull(id, "team id is required");
        this.competitionId = Objects.requireNonNull(competitionId, "competition id is required");
        this.categoryId = Objects.requireNonNull(categoryId, "category id is required");
        this.members = Objects.requireNonNull(members, "team members are required");
        this.robot = Objects.requireNonNull(robot, "robot is required");
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("team requires a name");
        }
        this.name = name;
        documents.forEach(document -> this.documents.put(document.type(), document));
    }

    public void resolveWith(EligibilityVerdict verdict) {
        Objects.requireNonNull(verdict, "eligibility verdict is required");
        if (status != RegistrationStatus.SUBMITTED) {
            throw new ConflictException("registration of team " + name + " was already resolved as " + status);
        }
        this.status = verdict.isEligible() ? RegistrationStatus.ACCEPTED : RegistrationStatus.REJECTED;
        this.rejectionReasons = verdict.violations();
    }

    public boolean isAccepted() {
        return status == RegistrationStatus.ACCEPTED;
    }

    public Set<DocumentType> documentTypes() {
        return Set.copyOf(documents.keySet());
    }

    public TeamId id() {
        return id;
    }

    public CompetitionId competitionId() {
        return competitionId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public String name() {
        return name;
    }

    public TeamMembers members() {
        return members;
    }

    public Robot robot() {
        return robot;
    }

    public RegistrationStatus status() {
        return status;
    }

    public List<EligibilityViolation> rejectionReasons() {
        return rejectionReasons;
    }
}

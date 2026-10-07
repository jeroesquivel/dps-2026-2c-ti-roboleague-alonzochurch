package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.TeamId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Standings {

    private final CompetitionId competitionId;
    private final CategoryId categoryId;
    private final RulebookVersion rulebookVersion;
    private final Revision revision;
    private final PublicationStatus status;
    private final Instant generatedAt;
    private final List<StandingEntry> entries;

    private Standings(CompetitionId competitionId, CategoryId categoryId, RulebookVersion rulebookVersion,
            Revision revision, PublicationStatus status, Instant generatedAt, List<StandingEntry> entries) {
        this.competitionId = Objects.requireNonNull(competitionId, "competition id is required");
        this.categoryId = Objects.requireNonNull(categoryId, "category id is required");
        this.rulebookVersion = Objects.requireNonNull(rulebookVersion, "rulebook version is required");
        this.revision = Objects.requireNonNull(revision, "revision is required");
        this.status = Objects.requireNonNull(status, "publication status is required");
        this.generatedAt = Objects.requireNonNull(generatedAt, "generation timestamp is required");
        this.entries = List.copyOf(entries);
    }

    public static Standings provisional(CompetitionId competitionId, CategoryId categoryId, RulebookVersion version,
            Instant generatedAt, List<StandingEntry> entries) {
        return new Standings(competitionId, categoryId, version, Revision.first(), PublicationStatus.PROVISIONAL,
                generatedAt, entries);
    }

    public Standings publish() {
        if (status == PublicationStatus.FINAL) {
            throw new ConflictException("standings revision " + revision + " is already final");
        }
        return new Standings(competitionId, categoryId, rulebookVersion, revision, PublicationStatus.FINAL,
                generatedAt, entries);
    }

    public Standings supersede(List<StandingEntry> newEntries, Instant recalculatedAt) {
        return new Standings(competitionId, categoryId, rulebookVersion, revision.next(),
                PublicationStatus.PROVISIONAL, recalculatedAt, newEntries);
    }

    public boolean isFinal() {
        return status == PublicationStatus.FINAL;
    }

    public Optional<StandingEntry> entryFor(TeamId teamId) {
        return entries.stream().filter(entry -> entry.teamId().equals(teamId)).findFirst();
    }

    public CompetitionId competitionId() {
        return competitionId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public RulebookVersion rulebookVersion() {
        return rulebookVersion;
    }

    public Revision revision() {
        return revision;
    }

    public PublicationStatus status() {
        return status;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public List<StandingEntry> entries() {
        return entries;
    }
}

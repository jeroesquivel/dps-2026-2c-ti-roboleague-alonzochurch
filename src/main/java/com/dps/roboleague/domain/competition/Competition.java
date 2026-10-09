package com.dps.roboleague.domain.competition;

import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.TimeSlot;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.ConflictException;
import com.dps.roboleague.domain.shared.DateRange;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RuleViolationException;
import com.dps.roboleague.domain.shared.SeasonId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Competition {

    private final CompetitionId id;
    private final SeasonId seasonId;
    private final String name;
    private final DateRange period;
    private final Map<CategoryId, Category> categories = new LinkedHashMap<>();
    private final RulebookVersion activeRulebookVersion;

    public Competition(CompetitionId id, SeasonId seasonId, String name, DateRange period,
            Collection<Category> categories, RulebookVersion activeRulebookVersion) {
        this.id = Objects.requireNonNull(id, "competition id is required");
        this.seasonId = Objects.requireNonNull(seasonId, "season id is required");
        this.period = Objects.requireNonNull(period, "competition period is required");
        this.activeRulebookVersion = Objects.requireNonNull(activeRulebookVersion,
                "a competition is created with its first rulebook version");
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("competition requires a name");
        }
        this.name = name;
        categories.forEach(this::addCategory);
    }

    private void addCategory(Category category) {
        Objects.requireNonNull(category, "category is required");
        if (categories.containsKey(category.id())) {
            throw new InvalidValueException("category " + category.id().value() + " is declared twice");
        }
        categories.put(category.id(), category);
    }

    public Category category(CategoryId categoryId) {
        Category category = categories.get(categoryId);
        if (category == null) {
            throw new NotFoundException("category " + categoryId.value() + " does not belong to competition " + name);
        }
        return category;
    }

    public void requireDateWithinPeriod(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        if (!period.contains(date)) {
            throw new RuleViolationException(
                    "date " + date + " is outside the period of competition " + name);
        }
    }

    public void requireSlotWithinPeriod(TimeSlot slot) {
        requireDateWithinPeriod(slot.start().toLocalDate());
        requireDateWithinPeriod(slot.end().toLocalDate());
    }

    public Competition activateRulebook(RulebookVersion version) {
        Objects.requireNonNull(version, "rulebook version is required");
        if (!version.isNewerThan(activeRulebookVersion)) {
            throw new ConflictException("rulebook version " + version + " does not supersede " + activeRulebookVersion);
        }
        return new Competition(id, seasonId, name, period, categories.values(), version);
    }

    public CompetitionId id() {
        return id;
    }

    public SeasonId seasonId() {
        return seasonId;
    }

    public String name() {
        return name;
    }

    public DateRange period() {
        return period;
    }

    public RulebookVersion activeRulebookVersion() {
        return activeRulebookVersion;
    }

    public List<Category> categories() {
        return List.copyOf(categories.values());
    }
}

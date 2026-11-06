package com.dps.roboleague.shared;

public record SeasonId(String value) implements Identifier {

    public SeasonId {
        Identifier.validate(value, "SeasonId");
    }

    public static SeasonId of(String value) {
        return new SeasonId(value);
    }
}

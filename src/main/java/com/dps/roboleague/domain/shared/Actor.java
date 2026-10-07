package com.dps.roboleague.domain.shared;

public record Actor(String name) {

    public Actor {
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("an actor requires a non blank name");
        }
        name = name.trim();
    }

    public static Actor of(String name) {
        return new Actor(name);
    }

    @Override
    public String toString() {
        return name;
    }
}

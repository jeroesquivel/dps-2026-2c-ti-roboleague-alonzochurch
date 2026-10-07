package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.InvalidValueException;

public record Revision(int number) implements Comparable<Revision> {

    public Revision {
        if (number < 1) {
            throw new InvalidValueException("a standings revision must be positive");
        }
    }

    public static Revision first() {
        return new Revision(1);
    }

    public static Revision of(int number) {
        return new Revision(number);
    }

    public Revision next() {
        return new Revision(number + 1);
    }

    @Override
    public int compareTo(Revision other) {
        return Integer.compare(number, other.number);
    }

    @Override
    public String toString() {
        return String.valueOf(number);
    }
}

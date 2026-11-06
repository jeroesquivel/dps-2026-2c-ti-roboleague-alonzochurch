package com.dps.roboleague.shared;

public interface Identifier {

    String value();

    static void validate(String value, String type) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(type + " requires a non blank value");
        }
    }
}

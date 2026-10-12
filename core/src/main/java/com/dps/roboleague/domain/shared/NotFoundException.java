package com.dps.roboleague.domain.shared;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String type, String id) {
        return new NotFoundException(type + " " + id + " was not found");
    }
}

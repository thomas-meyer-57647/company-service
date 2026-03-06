package de.innologic.companyservice.domain;

public class LocationNotFoundException extends ResourceNotFoundException {

    public static final String MESSAGE = "Location not found";

    public LocationNotFoundException() {
        super(MESSAGE);
    }
}

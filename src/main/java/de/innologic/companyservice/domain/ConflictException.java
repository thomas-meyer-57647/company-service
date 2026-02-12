package de.innologic.companyservice.domain;

public class ConflictException extends DomainException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

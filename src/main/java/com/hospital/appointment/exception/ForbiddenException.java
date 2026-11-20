package com.hospital.appointment.exception;

/** The user is logged in with the right role, but is trying to touch someone else's data. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

package com.quizapp.exception;

/** Thrown when the caller is authenticated (or public) but not allowed to do this. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}

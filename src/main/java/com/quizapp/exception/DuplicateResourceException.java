package com.quizapp.exception;

/** Thrown when creating something that already exists, e.g. a taken username. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}

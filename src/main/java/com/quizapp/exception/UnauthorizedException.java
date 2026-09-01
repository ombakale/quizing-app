package com.quizapp.exception;

/** Thrown when credentials are missing, wrong, or no longer valid. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

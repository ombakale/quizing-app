package com.quizapp.exception;

/**
 * Thrown for requests that are well-formed but break a business rule, such as an
 * answer referencing a question from another quiz.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

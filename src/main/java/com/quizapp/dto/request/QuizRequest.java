package com.quizapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Creation payload. Deliberately has no id field: a client-supplied id would otherwise turn
 * a create into a merge over an existing quiz.
 */
@Data
public class QuizRequest {

    @NotBlank(message = "Quiz title is required")
    @Size(max = 200, message = "Quiz title must be at most 200 characters")
    private String title;

    @Size(max = 1000, message = "Quiz description must be at most 1000 characters")
    private String description;

    @NotEmpty(message = "A quiz must contain at least one question")
    @Valid
    private List<QuestionRequest> questions;
}

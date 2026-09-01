package com.quizapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Only the fields an admin may change on an existing quiz. */
@Data
public class QuizUpdateRequest {

    @NotBlank(message = "Quiz title is required")
    @Size(max = 200, message = "Quiz title must be at most 200 characters")
    private String title;

    @Size(max = 1000, message = "Quiz description must be at most 1000 characters")
    private String description;
}

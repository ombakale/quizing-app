package com.quizapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuestionRequest {

    @NotBlank(message = "Question text is required")
    @Size(max = 1000, message = "Question text must be at most 1000 characters")
    private String text;

    @NotNull(message = "A question must have options")
    @Size(min = 2, message = "A question must have at least two options")
    @Valid
    private List<OptionRequest> options;
}

package com.quizapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OptionRequest {

    @NotBlank(message = "Option text is required")
    @Size(max = 500, message = "Option text must be at most 500 characters")
    private String text;

    private boolean correct;
}

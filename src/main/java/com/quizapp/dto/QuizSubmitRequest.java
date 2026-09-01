package com.quizapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitRequest {

    @NotNull(message = "answers is required (send an empty list to submit a blank attempt)")
    @Valid
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {

        @NotNull(message = "Each answer must include a questionId")
        private Long questionId;

        // null means the student deliberately skipped the question
        private Long selectedOptionId;
    }
}

package com.quizapp.dto;

import lombok.Data;
import java.util.List;

@Data
public class QuizSubmitRequest {
    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Long questionId;
        private Long selectedOptionId;
    }
}

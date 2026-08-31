package com.quizapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuizResultResponse {
    private Long attemptId;
    private Long quizId;
    private String quizTitle;
    private int score;
    private int totalQuestions;
    private double percentage;
    private List<QuestionDetail> details;

    @Data
    @AllArgsConstructor
    public static class QuestionDetail {
        private Long questionId;
        private String questionText;
        private Long selectedOptionId;
        private Long correctOptionId;
        private boolean correct;
    }
}

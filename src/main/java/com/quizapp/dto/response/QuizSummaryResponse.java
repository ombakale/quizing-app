package com.quizapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight quiz view for the quiz list.
 * Deliberately excludes questions/options so answer keys are never exposed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSummaryResponse {
    private Long id;
    private String title;
    private String description;
    private int totalQuestions;
}

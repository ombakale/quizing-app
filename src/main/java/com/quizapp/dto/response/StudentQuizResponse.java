package com.quizapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What a student receives when taking a quiz. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuizResponse {
    private Long id;
    private String title;
    private String description;
    private List<StudentQuestionResponse> questions;
}

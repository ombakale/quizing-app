package com.quizapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Student-facing option view. The type has no 'correct' field at all, so the answer key
 * cannot leak through this path even by accident.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOptionResponse {
    private Long id;
    private String text;
}

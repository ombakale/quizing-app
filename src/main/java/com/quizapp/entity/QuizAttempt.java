package com.quizapp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long quizId;
    private String quizTitle;
    private int score;
    private int totalQuestions;
    private double percentage;

    @Builder.Default
    private LocalDateTime attemptedAt = LocalDateTime.now();
}

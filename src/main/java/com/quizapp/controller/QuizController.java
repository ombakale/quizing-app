package com.quizapp.controller;

import com.quizapp.dto.QuizResultResponse;
import com.quizapp.dto.QuizSubmitRequest;
import com.quizapp.dto.QuizSummaryResponse;
import com.quizapp.entity.QuizAttempt;
import com.quizapp.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quizzes")
@Tag(name = "Student Quizzes", description = "Endpoints for viewing, taking, and submitting quizzes")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    @Operation(summary = "List all available quizzes")
    public ResponseEntity<List<QuizSummaryResponse>> getAllQuizzes() {
        return ResponseEntity.ok(quizService.listQuizzes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get quiz questions for taking the quiz")
    public ResponseEntity<Map<String, Object>> getQuizForUser(@PathVariable Long id) {
        return ResponseEntity.ok(quizService.getQuizForStudent(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit answers and receive calculated score")
    public ResponseEntity<QuizResultResponse> submitQuiz(@PathVariable Long id,
                                                         Authentication auth,
                                                         @Valid @RequestBody QuizSubmitRequest request) {
        return ResponseEntity.ok(quizService.submitQuiz(id, auth.getName(), request));
    }

    @GetMapping("/attempts")
    @Operation(summary = "Get user previous attempts")
    public ResponseEntity<List<QuizAttempt>> getUserAttempts(Authentication auth) {
        return ResponseEntity.ok(quizService.getAttempts(auth.getName()));
    }
}

package com.quizapp.controller;

import com.quizapp.dto.request.QuestionRequest;
import com.quizapp.dto.request.QuizRequest;
import com.quizapp.dto.request.QuizUpdateRequest;
import com.quizapp.dto.response.QuestionResponse;
import com.quizapp.dto.response.QuizResponse;
import com.quizapp.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Quiz Management", description = "Admin CRUD endpoints for Quizzes and Questions")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/quizzes")
    @Operation(summary = "Create a new quiz with questions")
    public ResponseEntity<QuizResponse> createQuiz(@Valid @RequestBody QuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createQuiz(request));
    }

    @PutMapping("/quizzes/{id}")
    @Operation(summary = "Update quiz title and description")
    public ResponseEntity<QuizResponse> updateQuiz(@PathVariable Long id,
                                                  @Valid @RequestBody QuizUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateQuiz(id, request));
    }

    @DeleteMapping("/quizzes/{id}")
    @Operation(summary = "Delete a quiz")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long id) {
        adminService.deleteQuiz(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Add a question to an existing quiz")
    public ResponseEntity<QuestionResponse> addQuestion(@PathVariable Long quizId,
                                                       @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addQuestion(quizId, request));
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "Delete a question")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        adminService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }
}

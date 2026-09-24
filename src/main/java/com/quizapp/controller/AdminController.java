package com.quizapp.controller;

import com.quizapp.dto.request.QuestionRequest;
import com.quizapp.dto.request.QuizRequest;
import com.quizapp.dto.request.QuizUpdateRequest;
import com.quizapp.dto.response.QuestionResponse;
import com.quizapp.dto.response.QuizResponse;
import com.quizapp.dto.response.UserResponse;
import com.quizapp.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Quiz Management", description = "Admin CRUD endpoints for Quizzes and Questions")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/quizzes")
    @Operation(summary = "List every quiz with its answer key",
               description = "The admin view. Students use GET /api/quizzes, whose response "
                           + "type has no 'correct' field at all.")
    public ResponseEntity<List<QuizResponse>> listQuizzes() {
        return ResponseEntity.ok(adminService.listQuizzes());
    }

    @GetMapping("/quizzes/{id}")
    @Operation(summary = "Read one quiz with its answer key",
               description = "What the edit screen loads before an update.")
    public ResponseEntity<QuizResponse> getQuiz(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getQuiz(id));
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

    @PutMapping("/questions/{id}")
    @Operation(summary = "Update a question's text and replace its options")
    public ResponseEntity<QuestionResponse> updateQuestion(@PathVariable Long id,
                                                           @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(adminService.updateQuestion(id, request));
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "Delete a question")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        adminService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    @Operation(summary = "List accounts",
               description = "Username, role and how many quizzes each account has submitted. "
                           + "Password hashes are not part of the response type.")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete an account and its attempt history",
               description = "An admin cannot delete their own account: it would invalidate "
                           + "the token mid-request and could leave nobody able to author.")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Authentication auth) {
        adminService.deleteUser(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}

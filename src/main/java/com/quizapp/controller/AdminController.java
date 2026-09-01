package com.quizapp.controller;

import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.repository.QuestionRepository;
import com.quizapp.repository.QuizRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin Quiz Management", description = "Admin CRUD endpoints for Quizzes and Questions")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @PostMapping("/quizzes")
    @Operation(summary = "Create a new quiz with questions")
    public ResponseEntity<?> createQuiz(@RequestBody Quiz quiz) {
        String error = validateQuiz(quiz);
        if (error != null) return badRequest(error);

        if (quiz.getQuestions() != null) {
            quiz.getQuestions().forEach(q -> {
                q.setQuiz(quiz);
                if (q.getOptions() != null) {
                    q.getOptions().forEach(o -> o.setQuestion(q));
                }
            });
        }
        Quiz savedQuiz = quizRepository.save(quiz);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedQuiz);
    }

    @PutMapping("/quizzes/{id}")
    @Operation(summary = "Update quiz title and description")
    public ResponseEntity<?> updateQuiz(@PathVariable Long id, @RequestBody Quiz details) {
        if (isBlank(details.getTitle())) return badRequest("Quiz title is required");

        return quizRepository.findById(id).<ResponseEntity<?>>map(quiz -> {
            quiz.setTitle(details.getTitle());
            quiz.setDescription(details.getDescription());
            return ResponseEntity.ok(quizRepository.save(quiz));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/quizzes/{id}")
    @Operation(summary = "Delete a quiz")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long id) {
        if (!quizRepository.existsById(id)) return ResponseEntity.notFound().build();
        quizRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Add a question to an existing quiz")
    public ResponseEntity<?> addQuestion(@PathVariable Long quizId, @RequestBody Question question) {
        String error = validateQuestion(question, 1);
        if (error != null) return badRequest(error);

        return quizRepository.findById(quizId).<ResponseEntity<?>>map(quiz -> {
            question.setQuiz(quiz);
            if (question.getOptions() != null) {
                question.getOptions().forEach(o -> o.setQuestion(question));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(questionRepository.save(question));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "Delete a question")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        if (!questionRepository.existsById(id)) return ResponseEntity.notFound().build();
        questionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * A quiz must be answerable and scorable: every question needs real text, at least two
     * options, and exactly one option flagged as correct.
     */
    private String validateQuiz(Quiz quiz) {
        if (isBlank(quiz.getTitle())) return "Quiz title is required";
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            return "A quiz must contain at least one question";
        }

        int position = 1;
        for (Question question : quiz.getQuestions()) {
            String error = validateQuestion(question, position++);
            if (error != null) return error;
        }
        return null;
    }

    private String validateQuestion(Question question, int position) {
        if (question == null) return "Question " + position + " is missing";
        if (isBlank(question.getText())) return "Question " + position + " must have text";

        List<Option> options = question.getOptions();
        if (options == null || options.size() < 2) {
            return "Question " + position + " must have at least two options";
        }
        if (options.stream().anyMatch(o -> o == null || isBlank(o.getText()))) {
            return "Question " + position + " has an option with no text";
        }

        long correctCount = options.stream().filter(Option::isCorrect).count();
        if (correctCount != 1) {
            return "Question " + position + " must have exactly one correct option (found " + correctCount + ")";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}

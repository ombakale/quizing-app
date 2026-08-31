package com.quizapp.controller;

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
    public ResponseEntity<Quiz> createQuiz(@RequestBody Quiz quiz) {
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
    public ResponseEntity<Quiz> updateQuiz(@PathVariable Long id, @RequestBody Quiz details) {
        return quizRepository.findById(id).map(quiz -> {
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
    public ResponseEntity<Question> addQuestion(@PathVariable Long quizId, @RequestBody Question question) {
        return quizRepository.findById(quizId).map(quiz -> {
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
}

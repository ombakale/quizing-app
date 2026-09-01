package com.quizapp.controller;

import com.quizapp.entity.Option;
import jakarta.validation.Valid;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.exception.BadRequestException;
import com.quizapp.exception.ResourceNotFoundException;
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
    public ResponseEntity<Quiz> createQuiz(@Valid @RequestBody Quiz quiz) {
        String error = validateQuiz(quiz);
        if (error != null) throw new BadRequestException(error);

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
    public ResponseEntity<Quiz> updateQuiz(@PathVariable Long id, @Valid @RequestBody Quiz details) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", id));

        quiz.setTitle(details.getTitle());
        quiz.setDescription(details.getDescription());
        return ResponseEntity.ok(quizRepository.save(quiz));
    }

    @DeleteMapping("/quizzes/{id}")
    @Operation(summary = "Delete a quiz")
    public ResponseEntity<Void> deleteQuiz(@PathVariable Long id) {
        if (!quizRepository.existsById(id)) throw new ResourceNotFoundException("Quiz", id);
        quizRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Add a question to an existing quiz")
    public ResponseEntity<Question> addQuestion(@PathVariable Long quizId, @Valid @RequestBody Question question) {
        String error = validateQuestion(question, 1);
        if (error != null) throw new BadRequestException(error);

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));

        question.setQuiz(quiz);
        if (question.getOptions() != null) {
            question.getOptions().forEach(o -> o.setQuestion(question));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(questionRepository.save(question));
    }

    @DeleteMapping("/questions/{id}")
    @Operation(summary = "Delete a question")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        if (!questionRepository.existsById(id)) throw new ResourceNotFoundException("Question", id);
        questionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * A quiz must be answerable and scorable: every question needs real text, at least two
     * options, and exactly one option flagged as correct.
     */
    private String validateQuiz(Quiz quiz) {
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

        List<Option> options = question.getOptions();
        if (options == null || options.size() < 2) {
            return "Question " + position + " must have at least two options";
        }
        if (options.stream().anyMatch(o -> o == null)) {
            return "Question " + position + " has an empty option entry";
        }

        long correctCount = options.stream().filter(Option::isCorrect).count();
        if (correctCount != 1) {
            return "Question " + position + " must have exactly one correct option (found " + correctCount + ")";
        }
        return null;
    }
}

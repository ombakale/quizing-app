package com.quizapp.controller;

import com.quizapp.dto.QuizResultResponse;
import com.quizapp.dto.QuizSummaryResponse;
import com.quizapp.dto.QuizSubmitRequest;
import com.quizapp.entity.*;
import com.quizapp.repository.QuizAttemptRepository;
import com.quizapp.repository.QuizRepository;
import com.quizapp.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/quizzes")
@Tag(name = "Student Quizzes", description = "Endpoints for viewing, taking, and submitting quizzes")
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @GetMapping
    @Operation(summary = "List all available quizzes")
    public ResponseEntity<List<QuizSummaryResponse>> getAllQuizzes() {
        List<QuizSummaryResponse> summaries = quizRepository.findAll().stream()
                .map(quiz -> QuizSummaryResponse.builder()
                        .id(quiz.getId())
                        .title(quiz.getTitle())
                        .description(quiz.getDescription())
                        .totalQuestions(quiz.getQuestions() != null ? quiz.getQuestions().size() : 0)
                        .build())
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get quiz questions for taking the quiz")
    public ResponseEntity<?> getQuizForUser(@PathVariable Long id) {
        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) return ResponseEntity.notFound().build();

        // Build student response (hide correctness flags)
        Map<String, Object> response = new HashMap<>();
        response.put("id", quiz.getId());
        response.put("title", quiz.getTitle());
        response.put("description", quiz.getDescription());

        List<Map<String, Object>> questionsList = new ArrayList<>();
        for (Question q : quiz.getQuestions()) {
            Map<String, Object> qMap = new HashMap<>();
            qMap.put("id", q.getId());
            qMap.put("text", q.getText());

            List<Map<String, Object>> optionsList = new ArrayList<>();
            for (Option o : q.getOptions()) {
                Map<String, Object> oMap = new HashMap<>();
                oMap.put("id", o.getId());
                oMap.put("text", o.getText());
                optionsList.add(oMap);
            }
            qMap.put("options", optionsList);
            questionsList.add(qMap);
        }
        response.put("questions", questionsList);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit answers and receive calculated score")
    public ResponseEntity<?> submitQuiz(
            @PathVariable Long id,
            Authentication auth,
            @RequestBody QuizSubmitRequest request) {

        Quiz quiz = quizRepository.findById(id).orElse(null);
        if (quiz == null) return ResponseEntity.notFound().build();

        User user = userRepository.findByUsername(auth.getName()).orElse(null);

        // Map submitted questionId -> selectedOptionId
        Map<Long, Long> submissionMap = new HashMap<>();
        if (request.getAnswers() != null) {
            for (QuizSubmitRequest.AnswerItem item : request.getAnswers()) {
                submissionMap.put(item.getQuestionId(), item.getSelectedOptionId());
            }
        }

        int score = 0;
        int totalQuestions = quiz.getQuestions().size();
        List<QuizResultResponse.QuestionDetail> details = new ArrayList<>();

        for (Question q : quiz.getQuestions()) {
            Long selectedOptionId = submissionMap.get(q.getId());
            Option correctOption = q.getOptions().stream().filter(Option::isCorrect).findFirst().orElse(null);
            Long correctOptionId = correctOption != null ? correctOption.getId() : null;

            boolean isCorrect = (selectedOptionId != null && selectedOptionId.equals(correctOptionId));
            if (isCorrect) score++;

            details.add(new QuizResultResponse.QuestionDetail(
                    q.getId(), q.getText(), selectedOptionId, correctOptionId, isCorrect
            ));
        }

        double percentage = totalQuestions > 0 ? (double) score / totalQuestions * 100.0 : 0.0;

        // Save Attempt
        QuizAttempt attempt = QuizAttempt.builder()
                .userId(user != null ? user.getId() : null)
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .score(score)
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .build();

        quizAttemptRepository.save(attempt);

        QuizResultResponse result = QuizResultResponse.builder()
                .attemptId(attempt.getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .score(score)
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .details(details)
                .build();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/attempts")
    @Operation(summary = "Get user previous attempts")
    public ResponseEntity<?> getUserAttempts(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(quizAttemptRepository.findByUserId(user.getId()));
    }
}

package com.quizapp.controller;

import com.quizapp.dto.QuizResultResponse;
import com.quizapp.exception.BadRequestException;
import com.quizapp.exception.ResourceNotFoundException;
import com.quizapp.exception.UnauthorizedException;
import jakarta.validation.Valid;
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
    public ResponseEntity<Map<String, Object>> getQuizForUser(@PathVariable Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", id));

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
    public ResponseEntity<QuizResultResponse> submitQuiz(
            @PathVariable Long id,
            Authentication auth,
            @Valid @RequestBody QuizSubmitRequest request) {

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", id));

        User user = currentUser(auth);

        Map<Long, Question> questionsById = new HashMap<>();
        for (Question q : quiz.getQuestions()) {
            questionsById.put(q.getId(), q);
        }

        // Validate every submitted answer actually belongs to this quiz before scoring anything
        Map<Long, Long> submissionMap = new HashMap<>();
        if (request.getAnswers() != null) {
            for (QuizSubmitRequest.AnswerItem item : request.getAnswers()) {
                Long questionId = item.getQuestionId();
                Question question = questionsById.get(questionId);
                if (question == null) {
                    throw new BadRequestException("Question " + questionId + " does not belong to quiz " + id);
                }

                Long selectedOptionId = item.getSelectedOptionId();
                if (selectedOptionId != null
                        && question.getOptions().stream().noneMatch(o -> selectedOptionId.equals(o.getId()))) {
                    throw new BadRequestException(
                            "Option " + selectedOptionId + " does not belong to question " + questionId);
                }

                // containsKey rather than the return of put(): a previous answer with a null
                // selectedOptionId also makes put() return null, hiding the duplicate.
                if (submissionMap.containsKey(questionId)) {
                    throw new BadRequestException("Duplicate answer submitted for question " + questionId);
                }
                submissionMap.put(questionId, selectedOptionId);
            }
        }

        int score = 0;
        int totalQuestions = 0;
        List<QuizResultResponse.QuestionDetail> details = new ArrayList<>();

        for (Question q : quiz.getQuestions()) {
            Long selectedOptionId = submissionMap.get(q.getId());
            List<Option> correctOptions = q.getOptions().stream().filter(Option::isCorrect).toList();

            // A question without exactly one correct option cannot be scored fairly, so it is
            // left out of the total instead of being counted against the student.
            if (correctOptions.size() != 1) {
                details.add(new QuizResultResponse.QuestionDetail(
                        q.getId(), q.getText(), selectedOptionId, null, false
                ));
                continue;
            }

            totalQuestions++;
            Long correctOptionId = correctOptions.get(0).getId();
            boolean isCorrect = correctOptionId.equals(selectedOptionId);
            if (isCorrect) score++;

            details.add(new QuizResultResponse.QuestionDetail(
                    q.getId(), q.getText(), selectedOptionId, correctOptionId, isCorrect
            ));
        }

        double percentage = totalQuestions > 0 ? (double) score / totalQuestions * 100.0 : 0.0;

        // Save Attempt
        QuizAttempt attempt = QuizAttempt.builder()
                .userId(user.getId())
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

    private User currentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
    }

    @GetMapping("/attempts")
    @Operation(summary = "Get user previous attempts")
    public ResponseEntity<List<QuizAttempt>> getUserAttempts(Authentication auth) {
        return ResponseEntity.ok(quizAttemptRepository.findByUserId(currentUser(auth).getId()));
    }
}

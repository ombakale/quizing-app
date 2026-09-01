package com.quizapp.service;

import com.quizapp.dto.QuizResultResponse;
import com.quizapp.dto.QuizSubmitRequest;
import com.quizapp.dto.response.QuizAttemptResponse;
import com.quizapp.dto.response.QuizSummaryResponse;
import com.quizapp.dto.response.StudentQuizResponse;
import com.quizapp.mapper.QuizMapper;
import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.entity.QuizAttempt;
import com.quizapp.entity.User;
import com.quizapp.exception.BadRequestException;
import com.quizapp.exception.ResourceNotFoundException;
import com.quizapp.exception.UnauthorizedException;
import com.quizapp.repository.QuizAttemptRepository;
import com.quizapp.repository.QuizRepository;
import com.quizapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything a student can do: browse quizzes, take one, and review past attempts. */
@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public QuizService(QuizRepository quizRepository,
                       UserRepository userRepository,
                       QuizAttemptRepository quizAttemptRepository) {
        this.quizRepository = quizRepository;
        this.userRepository = userRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @Transactional(readOnly = true)
    public List<QuizSummaryResponse> listQuizzes() {
        return quizRepository.findAllSummaries();
    }

    /**
     * The student view of a quiz. StudentQuizResponse has no 'correct' field anywhere in its
     * type graph, so the answer key cannot be serialised down this path.
     */
    @Transactional(readOnly = true)
    public StudentQuizResponse getQuizForStudent(Long quizId) {
        return QuizMapper.toStudentResponse(requireQuiz(quizId));
    }

    @Transactional
    public QuizResultResponse submitQuiz(Long quizId, String username, QuizSubmitRequest request) {
        Quiz quiz = requireQuiz(quizId);
        User user = requireUser(username);

        requireScorable(quiz);
        Map<Long, Long> submission = validateAnswers(quiz, request);
        return score(quiz, user, submission);
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptResponse> getAttempts(String username) {
        return quizAttemptRepository.findByUserIdOrderByAttemptedAtDescIdDesc(requireUser(username).getId()).stream()
                .map(QuizMapper::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------ internals

    /**
     * Rejects the whole submission before any scoring happens if an answer references a
     * question outside this quiz, an option outside that question, or answers a question twice.
     *
     * @return questionId to selectedOptionId, where a null value means the question was skipped
     */
    private Map<Long, Long> validateAnswers(Quiz quiz, QuizSubmitRequest request) {
        Map<Long, Question> questionsById = new HashMap<>();
        for (Question question : quiz.getQuestions()) {
            questionsById.put(question.getId(), question);
        }

        Map<Long, Long> submission = new HashMap<>();
        if (request.getAnswers() == null) {
            return submission;
        }

        for (QuizSubmitRequest.AnswerItem item : request.getAnswers()) {
            Long questionId = item.getQuestionId();
            Question question = questionsById.get(questionId);
            if (question == null) {
                throw new BadRequestException(
                        "Question " + questionId + " does not belong to quiz " + quiz.getId());
            }

            Long selectedOptionId = item.getSelectedOptionId();
            if (selectedOptionId != null
                    && question.getOptions().stream().noneMatch(o -> selectedOptionId.equals(o.getId()))) {
                throw new BadRequestException(
                        "Option " + selectedOptionId + " does not belong to question " + questionId);
            }

            // containsKey rather than the return of put(): a previous answer with a null
            // selectedOptionId also makes put() return null, hiding the duplicate.
            if (submission.containsKey(questionId)) {
                throw new BadRequestException("Duplicate answer submitted for question " + questionId);
            }
            submission.put(questionId, selectedOptionId);
        }
        return submission;
    }

    /**
     * A question with zero or several correct options cannot be judged. Rather than quietly
     * dropping it from the denominator - which would make one quiz report different totals to
     * different students, and hand out a 7/7 for a 10-question quiz - the submission is refused
     * so an admin fixes the quiz. Content authored through AdminService can never reach this
     * state; quizzes predating that validation can.
     */
    private void requireScorable(Quiz quiz) {
        for (Question question : quiz.getQuestions()) {
            long correctCount = question.getOptions().stream().filter(Option::isCorrect).count();
            if (correctCount != 1) {
                throw new BadRequestException("Quiz " + quiz.getId() + " cannot be scored: question "
                        + question.getId() + " has " + correctCount + " correct options, expected exactly one");
            }
        }
    }

    private QuizResultResponse score(Quiz quiz, User user, Map<Long, Long> submission) {
        int score = 0;
        int totalQuestions = quiz.getQuestions().size();
        List<QuizResultResponse.QuestionDetail> details = new ArrayList<>();

        for (Question question : quiz.getQuestions()) {
            Long selectedOptionId = submission.get(question.getId());
            Long correctOptionId = question.getOptions().stream()
                    .filter(Option::isCorrect)
                    .findFirst()
                    .map(Option::getId)
                    .orElseThrow();

            boolean isCorrect = correctOptionId.equals(selectedOptionId);
            if (isCorrect) score++;

            details.add(new QuizResultResponse.QuestionDetail(
                    question.getId(), question.getText(), selectedOptionId, correctOptionId, isCorrect));
        }

        double percentage = totalQuestions > 0 ? (double) score / totalQuestions * 100.0 : 0.0;

        QuizAttempt attempt = quizAttemptRepository.save(QuizAttempt.builder()
                .user(user)
                .quiz(quiz)
                .quizTitle(quiz.getTitle())
                .score(score)
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .build());

        return QuizResultResponse.builder()
                .attemptId(attempt.getId())
                .quizId(quiz.getId())
                .quizTitle(quiz.getTitle())
                .score(score)
                .totalQuestions(totalQuestions)
                .percentage(percentage)
                .details(details)
                .build();
    }

    private Quiz requireQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
    }
}

package com.quizapp.service;

import com.quizapp.dto.request.QuestionRequest;
import com.quizapp.dto.request.QuizRequest;
import com.quizapp.dto.request.QuizUpdateRequest;
import com.quizapp.dto.response.QuestionResponse;
import com.quizapp.dto.response.QuizResponse;
import com.quizapp.dto.response.UserResponse;
import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.entity.User;
import com.quizapp.mapper.QuizMapper;
import com.quizapp.exception.BadRequestException;
import com.quizapp.exception.ResourceNotFoundException;
import com.quizapp.repository.QuestionRepository;
import com.quizapp.repository.QuizAttemptRepository;
import com.quizapp.repository.QuizRepository;
import com.quizapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Admin surface: quiz authoring, plus the small amount of user administration the UI needs. */
@Service
public class AdminService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;

    public AdminService(QuizRepository quizRepository,
                        QuestionRepository questionRepository,
                        QuizAttemptRepository quizAttemptRepository,
                        UserRepository userRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------ quizzes, reading

    /**
     * The admin view of every quiz, answer key included. Students get
     * {@code GET /api/quizzes}, whose response type has no {@code correct} field at all.
     */
    @Transactional(readOnly = true)
    public List<QuizResponse> listQuizzes() {
        return quizRepository.findAll().stream()
                .sorted(Comparator.comparing(Quiz::getId))
                .map(QuizMapper::toResponse)
                .toList();
    }

    /** One quiz with its answer key - what the edit screen loads. */
    @Transactional(readOnly = true)
    public QuizResponse getQuiz(Long quizId) {
        return QuizMapper.toResponse(requireQuiz(quizId));
    }

    // ------------------------------------------------------------------ quizzes, writing

    @Transactional
    public QuizResponse createQuiz(QuizRequest request) {
        Quiz quiz = QuizMapper.toEntity(request);
        requireExactlyOneCorrectOption(quiz.getQuestions());

        return QuizMapper.toResponse(quizRepository.save(quiz));
    }

    @Transactional
    public QuizResponse updateQuiz(Long quizId, QuizUpdateRequest request) {
        Quiz quiz = requireQuiz(quizId);

        quiz.setTitle(request.getTitle());
        quiz.setDescription(request.getDescription());
        return QuizMapper.toResponse(quizRepository.save(quiz));
    }

    @Transactional
    public void deleteQuiz(Long quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw new ResourceNotFoundException("Quiz", quizId);
        }
        // Past attempts outlive the quiz, so detach them rather than let the FK block the delete
        quizAttemptRepository.detachFromQuiz(quizId);
        quizRepository.deleteById(quizId);
    }

    @Transactional
    public QuestionResponse addQuestion(Long quizId, QuestionRequest request) {
        Quiz quiz = requireQuiz(quizId);

        Question question = QuizMapper.toEntity(request);
        requireExactlyOneCorrectOption(question, "The question");
        question.setQuiz(quiz);

        return QuizMapper.toResponse(questionRepository.save(question));
    }

    /**
     * Replaces a question's text and its whole option set. Options are replaced rather than
     * patched because their ids are meaningless to the caller and a partial update would leave
     * the question in a half-scorable state.
     */
    @Transactional
    public QuestionResponse updateQuestion(Long questionId, QuestionRequest request) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));

        Question replacement = QuizMapper.toEntity(request);
        requireExactlyOneCorrectOption(replacement, "Question " + questionId);

        question.setText(replacement.getText());
        // orphanRemoval deletes the old rows; clear() in place so Hibernate tracks the change
        question.getOptions().clear();
        replacement.getOptions().forEach(option -> {
            option.setQuestion(question);
            question.getOptions().add(option);
        });

        return QuizMapper.toResponse(questionRepository.save(question));
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        questionRepository.deleteById(questionId);
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .attemptCount(quizAttemptRepository.countByUserId(user.getId()))
                        .build())
                .toList();
    }

    /**
     * Removes an account and the attempt history that belongs to it.
     *
     * <p>Attempts are deleted rather than detached, the opposite of
     * {@link #deleteQuiz(Long)}: a quiz's history is a record of what students did and
     * outlives the quiz, whereas a person's attempts are that person's and should leave
     * with them.
     *
     * @param requestedBy the admin making the call, who may not delete their own account -
     *                    doing so would invalidate the token mid-request and, if they were
     *                    the last admin, leave nobody able to author anything.
     */
    @Transactional
    public void deleteUser(Long userId, String requestedBy) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getUsername().equals(requestedBy)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        quizAttemptRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    // ------------------------------------------------------------------ internals

    /**
     * The one rule bean validation cannot express: a question is only scorable when exactly one
     * of its options is flagged correct. Text, length and option count are already enforced by
     * the request DTOs before this point.
     */
    private void requireExactlyOneCorrectOption(List<Question> questions) {
        int position = 1;
        for (Question question : questions) {
            requireExactlyOneCorrectOption(question, "Question " + position++);
        }
    }

    private void requireExactlyOneCorrectOption(Question question, String label) {
        long correctCount = question.getOptions().stream().filter(Option::isCorrect).count();
        if (correctCount != 1) {
            throw new BadRequestException(
                    label + " must have exactly one correct option (found " + correctCount + ")");
        }
    }

    private Quiz requireQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
    }
}

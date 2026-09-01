package com.quizapp.service;

import com.quizapp.dto.request.QuestionRequest;
import com.quizapp.dto.request.QuizRequest;
import com.quizapp.dto.request.QuizUpdateRequest;
import com.quizapp.dto.response.QuestionResponse;
import com.quizapp.dto.response.QuizResponse;
import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.mapper.QuizMapper;
import com.quizapp.exception.BadRequestException;
import com.quizapp.exception.ResourceNotFoundException;
import com.quizapp.repository.QuestionRepository;
import com.quizapp.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Quiz authoring: create, update and delete quizzes and their questions. */
@Service
public class AdminService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    public AdminService(QuizRepository quizRepository, QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

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
        quizRepository.deleteById(quizId);
    }

    @Transactional
    public QuestionResponse addQuestion(Long quizId, QuestionRequest request) {
        Quiz quiz = requireQuiz(quizId);

        Question question = QuizMapper.toEntity(request);
        requireExactlyOneCorrectOption(question, 1);
        question.setQuiz(quiz);

        return QuizMapper.toResponse(questionRepository.save(question));
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        questionRepository.deleteById(questionId);
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
            requireExactlyOneCorrectOption(question, position++);
        }
    }

    private void requireExactlyOneCorrectOption(Question question, int position) {
        long correctCount = question.getOptions().stream().filter(Option::isCorrect).count();
        if (correctCount != 1) {
            throw new BadRequestException(
                    "Question " + position + " must have exactly one correct option (found " + correctCount + ")");
        }
    }

    private Quiz requireQuiz(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId));
    }
}

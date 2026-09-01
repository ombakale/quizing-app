package com.quizapp.service;

import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
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
    public Quiz createQuiz(Quiz quiz) {
        requireValidQuiz(quiz);

        // Ids arriving in the body would turn save() into a merge, silently overwriting an
        // existing quiz (and, through orphanRemoval, deleting its questions). Creation always
        // means new rows.
        quiz.setId(null);
        quiz.getQuestions().forEach(question -> {
            question.setId(null);
            question.setQuiz(quiz);
            linkOptions(question);
        });

        return quizRepository.save(quiz);
    }

    @Transactional
    public Quiz updateQuiz(Long quizId, Quiz details) {
        Quiz quiz = requireQuiz(quizId);

        // Only these two fields are updatable; any id or questions in the body are ignored
        quiz.setTitle(details.getTitle());
        quiz.setDescription(details.getDescription());
        return quizRepository.save(quiz);
    }

    @Transactional
    public void deleteQuiz(Long quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw new ResourceNotFoundException("Quiz", quizId);
        }
        quizRepository.deleteById(quizId);
    }

    @Transactional
    public Question addQuestion(Long quizId, Question question) {
        requireValidQuestion(question, 1);
        Quiz quiz = requireQuiz(quizId);

        // Same reasoning as createQuiz: a body-supplied id must not hijack an existing row
        question.setId(null);
        question.setQuiz(quiz);
        linkOptions(question);

        return questionRepository.save(question);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        questionRepository.deleteById(questionId);
    }

    // ------------------------------------------------------------------ internals

    private void linkOptions(Question question) {
        if (question.getOptions() != null) {
            question.getOptions().forEach(option -> {
                option.setId(null);
                option.setQuestion(question);
            });
        }
    }

    /**
     * A quiz must be answerable and scorable: every question needs at least two options and
     * exactly one option flagged as correct. Blank text and length limits are enforced by bean
     * validation before the request reaches this point.
     */
    private void requireValidQuiz(Quiz quiz) {
        if (quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new BadRequestException("A quiz must contain at least one question");
        }

        int position = 1;
        for (Question question : quiz.getQuestions()) {
            requireValidQuestion(question, position++);
        }
    }

    private void requireValidQuestion(Question question, int position) {
        if (question == null) {
            throw new BadRequestException("Question " + position + " is missing");
        }

        List<Option> options = question.getOptions();
        if (options == null || options.size() < 2) {
            throw new BadRequestException("Question " + position + " must have at least two options");
        }
        if (options.stream().anyMatch(option -> option == null)) {
            throw new BadRequestException("Question " + position + " has an empty option entry");
        }

        long correctCount = options.stream().filter(Option::isCorrect).count();
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

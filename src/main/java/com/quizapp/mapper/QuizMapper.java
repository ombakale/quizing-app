package com.quizapp.mapper;

import com.quizapp.dto.request.OptionRequest;
import com.quizapp.dto.request.QuestionRequest;
import com.quizapp.dto.request.QuizRequest;
import com.quizapp.dto.response.OptionResponse;
import com.quizapp.dto.response.QuestionResponse;
import com.quizapp.dto.response.QuizAttemptResponse;
import com.quizapp.dto.response.QuizResponse;
import com.quizapp.dto.response.QuizSummaryResponse;
import com.quizapp.dto.response.StudentOptionResponse;
import com.quizapp.dto.response.StudentQuestionResponse;
import com.quizapp.dto.response.StudentQuizResponse;
import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.entity.QuizAttempt;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates between the JPA entities and the API payloads. Keeping this in one place is what
 * lets the student and admin views of a quiz differ by type rather than by remembering to strip
 * a field at each call site.
 */
public final class QuizMapper {

    private QuizMapper() {
    }

    // ---------------------------------------------------------------- request -> entity

    public static Quiz toEntity(QuizRequest request) {
        Quiz quiz = Quiz.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .questions(new ArrayList<>())
                .build();

        if (request.getQuestions() != null) {
            request.getQuestions().forEach(questionRequest -> {
                Question question = toEntity(questionRequest);
                question.setQuiz(quiz);
                quiz.getQuestions().add(question);
            });
        }
        return quiz;
    }

    public static Question toEntity(QuestionRequest request) {
        Question question = Question.builder()
                .text(request.getText())
                .options(new ArrayList<>())
                .build();

        if (request.getOptions() != null) {
            request.getOptions().forEach(optionRequest -> {
                Option option = toEntity(optionRequest);
                option.setQuestion(question);
                question.getOptions().add(option);
            });
        }
        return question;
    }

    public static Option toEntity(OptionRequest request) {
        return Option.builder()
                .text(request.getText())
                .correct(request.isCorrect())
                .build();
    }

    // ---------------------------------------------------------------- entity -> admin view

    public static QuizResponse toResponse(Quiz quiz) {
        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .questions(quiz.getQuestions().stream().map(QuizMapper::toResponse).toList())
                .build();
    }

    public static QuestionResponse toResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .text(question.getText())
                .options(question.getOptions().stream()
                        .map(option -> OptionResponse.builder()
                                .id(option.getId())
                                .text(option.getText())
                                .correct(option.isCorrect())
                                .build())
                        .toList())
                .build();
    }

    // ---------------------------------------------------------------- entity -> student view

    public static QuizSummaryResponse toSummary(Quiz quiz) {
        return QuizSummaryResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .totalQuestions(quiz.getQuestions().size())
                .build();
    }

    public static StudentQuizResponse toStudentResponse(Quiz quiz) {
        List<StudentQuestionResponse> questions = quiz.getQuestions().stream()
                .map(question -> StudentQuestionResponse.builder()
                        .id(question.getId())
                        .text(question.getText())
                        .options(question.getOptions().stream()
                                .map(option -> StudentOptionResponse.builder()
                                        .id(option.getId())
                                        .text(option.getText())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return StudentQuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .questions(questions)
                .build();
    }

    // ---------------------------------------------------------------- attempts

    public static QuizAttemptResponse toResponse(QuizAttempt attempt) {
        return QuizAttemptResponse.builder()
                .id(attempt.getId())
                // null once the quiz has been deleted; quizTitle keeps the history readable
                .quizId(attempt.getQuiz() != null ? attempt.getQuiz().getId() : null)
                .quizTitle(attempt.getQuizTitle())
                .score(attempt.getScore())
                .totalQuestions(attempt.getTotalQuestions())
                .percentage(attempt.getPercentage())
                .attemptedAt(attempt.getAttemptedAt())
                .build();
    }
}

package com.quizapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizapp.entity.Option;
import com.quizapp.entity.Question;
import com.quizapp.entity.Quiz;
import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuizSubmissionTest extends ApiTestBase {

    private String studentToken;
    private long quizId;
    private long firstQuestionId;
    private long firstCorrectOptionId;
    private long firstWrongOptionId;
    private long secondQuestionId;
    private long secondCorrectOptionId;

    @BeforeEach
    void seedQuiz() throws Exception {
        String adminToken = registerAdmin("boss");
        studentToken = registerStudent("student");

        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Maths"));
        quizId = quiz.get("id").asLong();

        JsonNode first = quiz.get("questions").get(0);
        firstQuestionId = first.get("id").asLong();
        firstCorrectOptionId = first.get("options").get(0).get("id").asLong();
        firstWrongOptionId = first.get("options").get(1).get("id").asLong();

        JsonNode second = quiz.get("questions").get(1);
        secondQuestionId = second.get("id").asLong();
        secondCorrectOptionId = second.get("options").get(0).get("id").asLong();
    }

    private Map<String, Object> answers(List<Map<String, Object>> items) {
        return Map.of("answers", items);
    }

    private Map<String, Object> answer(long questionId, Long optionId) {
        Map<String, Object> item = new HashMap<>();
        item.put("questionId", questionId);
        item.put("selectedOptionId", optionId);
        return item;
    }

    @Test
    @DisplayName("all correct scores full marks")
    void scoresEverythingCorrect() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, firstCorrectOptionId),
                        answer(secondQuestionId, secondCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.percentage").value(100.0))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].correct").value(true));
    }

    @Test
    @DisplayName("a wrong answer halves the score and reveals the right option afterwards")
    void scoresPartiallyCorrect() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, firstWrongOptionId),
                        answer(secondQuestionId, secondCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.percentage").value(50.0))
                .andExpect(jsonPath("$.details[0].correct").value(false))
                .andExpect(jsonPath("$.details[0].correctOptionId").value((int) firstCorrectOptionId));
    }

    @Test
    @DisplayName("skipped questions count against the total but are not an error")
    void scoresSkippedQuestions() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, firstCorrectOptionId),
                        answer(secondQuestionId, null))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.details[1].selectedOptionId").doesNotExist());
    }

    @Test
    @DisplayName("an empty submission scores zero out of the full question count")
    void scoresEmptySubmission() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of()))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.percentage").value(0.0));
    }

    @Test
    @DisplayName("an option from another question is refused")
    void rejectsForeignOption() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, secondCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Option " + secondCorrectOptionId + " does not belong to question " + firstQuestionId));
    }

    @Test
    @DisplayName("a question from another quiz is refused")
    void rejectsForeignQuestion() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(9999L, firstCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Question 9999 does not belong to quiz " + quizId));
    }

    @Test
    @DisplayName("answering the same question twice is refused, even when the first is a skip")
    void rejectsDuplicateAnswers() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, null),
                        answer(firstQuestionId, firstCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Duplicate answer submitted for question " + firstQuestionId));
    }

    @Test
    @DisplayName("an answer without a questionId fails validation")
    void rejectsAnswerWithoutQuestionId() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"),
                        Map.of("answers", List.of(Map.of("selectedOptionId", firstCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['answers[0].questionId']").exists());
    }

    @Test
    @DisplayName("a submission without an answers list fails validation")
    void rejectsMissingAnswersList() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), Map.of())
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.answers").exists());
    }

    @Test
    @DisplayName("submitting to a quiz that does not exist is 404")
    void rejectsMissingQuiz() throws Exception {
        mockMvc.perform(json(post("/api/quizzes/9999/submit"), answers(List.of()))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a quiz with an unscorable question is refused rather than scored on a smaller total")
    void rejectsUnscorableQuiz() throws Exception {
        // Built through the repository on purpose: the API cannot create this state, but
        // content predating the validation can be in this shape.
        Quiz broken = Quiz.builder().title("Legacy").questions(new ArrayList<>()).build();
        Question question = Question.builder().text("No right answer").options(new ArrayList<>()).build();
        question.setQuiz(broken);
        for (String text : List.of("a", "b")) {
            Option option = Option.builder().text(text).correct(false).build();
            option.setQuestion(question);
            question.getOptions().add(option);
        }
        broken.getQuestions().add(question);
        Quiz saved = quizRepository.save(broken);

        mockMvc.perform(json(post("/api/quizzes/" + saved.getId() + "/submit"), answers(List.of()))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "cannot be scored")));
    }

    @Test
    @DisplayName("attempt history comes back newest first")
    void ordersAttemptsNewestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                            answer(firstQuestionId, firstCorrectOptionId))))
                            .header("Authorization", bearer(studentToken)))
                    .andExpect(status().isOk());
        }

        String body = mockMvc.perform(get("/api/quizzes/attempts")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode attempts = objectMapper.readTree(body);
        long newest = attempts.get(0).get("id").asLong();
        long oldest = attempts.get(2).get("id").asLong();
        assertTrue(newest > oldest, "attempts were not newest-first: " + body);
        assertFalse(body.contains("userId"), "attempt history exposed userId: " + body);
    }

    @Test
    @DisplayName("deleting a quiz keeps the attempt history readable")
    void keepsHistoryAfterQuizDeletion() throws Exception {
        String adminToken = registerAdmin("boss2");

        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers(List.of(
                        answer(firstQuestionId, firstCorrectOptionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/quizzes/" + quizId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/quizzes/attempts").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quizId").doesNotExist())
                .andExpect(jsonPath("$[0].quizTitle").value("Maths"));
    }
}

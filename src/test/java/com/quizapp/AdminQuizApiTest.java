package com.quizapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminQuizApiTest extends ApiTestBase {

    private String adminToken;

    @BeforeEach
    void createAdmin() throws Exception {
        adminToken = registerAdmin("boss");
    }

    @Test
    @DisplayName("an admin can create a quiz and gets the answer key back")
    void createsQuiz() throws Exception {
        mockMvc.perform(json(post("/api/admin/quizzes"), twoQuestionQuiz("Maths"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Maths"))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(true));
    }

    @Test
    @DisplayName("an id in the body cannot hijack an existing quiz")
    void ignoresSuppliedIds() throws Exception {
        long existingId = createQuiz(adminToken, twoQuestionQuiz("Original")).get("id").asLong();

        Map<String, Object> hijack = Map.of(
                "id", existingId,
                "title", "Hijacked",
                "questions", List.of(Map.of("text", "q", "options", List.of(
                        Map.of("text", "a", "correct", true),
                        Map.of("text", "b", "correct", false)))));

        mockMvc.perform(json(post("/api/admin/quizzes"), hijack)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not((int) existingId)));

        assertEquals("Original", quizRepository.findById(existingId).orElseThrow().getTitle(),
                "the existing quiz was overwritten by a body-supplied id");
    }

    @Test
    @DisplayName("a quiz needs at least one question")
    void rejectsEmptyQuiz() throws Exception {
        mockMvc.perform(json(post("/api/admin/quizzes"),
                        Map.of("title", "Empty", "questions", List.of()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.questions").exists());
    }

    @Test
    @DisplayName("a question needs at least two options")
    void rejectsSingleOptionQuestion() throws Exception {
        Map<String, Object> quiz = Map.of("title", "Thin", "questions", List.of(
                Map.of("text", "q", "options", List.of(Map.of("text", "only", "correct", true)))));

        mockMvc.perform(json(post("/api/admin/quizzes"), quiz)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['questions[0].options']").exists());
    }

    @Test
    @DisplayName("a question needs exactly one correct option")
    void rejectsTwoCorrectOptions() throws Exception {
        Map<String, Object> quiz = Map.of("title", "Ambiguous", "questions", List.of(
                Map.of("text", "q", "options", List.of(
                        Map.of("text", "a", "correct", true),
                        Map.of("text", "b", "correct", true)))));

        mockMvc.perform(json(post("/api/admin/quizzes"), quiz)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Question 1 must have exactly one correct option (found 2)"));
    }

    @Test
    @DisplayName("blank text is rejected field by field")
    void rejectsBlankText() throws Exception {
        Map<String, Object> quiz = Map.of("title", " ", "questions", List.of(
                Map.of("text", "q", "options", List.of(
                        Map.of("text", "", "correct", true),
                        Map.of("text", "b", "correct", false)))));

        mockMvc.perform(json(post("/api/admin/quizzes"), quiz)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors['questions[0].options[0].text']").exists());
    }

    @Test
    @DisplayName("updating a quiz changes only title and description")
    void updatesQuiz() throws Exception {
        long quizId = createQuiz(adminToken, twoQuestionQuiz("Before")).get("id").asLong();

        mockMvc.perform(json(put("/api/admin/quizzes/" + quizId),
                        Map.of("title", "After", "description", "edited"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After"))
                .andExpect(jsonPath("$.description").value("edited"))
                .andExpect(jsonPath("$.questions.length()").value(2));
    }

    @Test
    @DisplayName("updating a quiz that does not exist is 404")
    void updateMissingQuiz() throws Exception {
        mockMvc.perform(json(put("/api/admin/quizzes/9999"), Map.of("title", "Nope"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Quiz 9999 not found"));
    }

    @Test
    @DisplayName("deleting a quiz removes it from the list")
    void deletesQuiz() throws Exception {
        long quizId = createQuiz(adminToken, twoQuestionQuiz("Doomed")).get("id").asLong();

        mockMvc.perform(delete("/api/admin/quizzes/" + quizId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/quizzes").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("deleting a quiz that does not exist is 404")
    void deleteMissingQuiz() throws Exception {
        mockMvc.perform(delete("/api/admin/quizzes/9999")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a question can be added to an existing quiz")
    void addsQuestion() throws Exception {
        long quizId = createQuiz(adminToken, twoQuestionQuiz("Growing")).get("id").asLong();

        mockMvc.perform(json(post("/api/admin/quizzes/" + quizId + "/questions"),
                        Map.of("text", "5 - 1 = ?", "options", List.of(
                                Map.of("text", "4", "correct", true),
                                Map.of("text", "3", "correct", false))))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("5 - 1 = ?"));

        mockMvc.perform(get("/api/quizzes/" + quizId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.questions.length()").value(3));
    }

    @Test
    @DisplayName("adding a question to a missing quiz is 404")
    void addQuestionToMissingQuiz() throws Exception {
        mockMvc.perform(json(post("/api/admin/quizzes/9999/questions"),
                        Map.of("text", "q", "options", List.of(
                                Map.of("text", "a", "correct", true),
                                Map.of("text", "b", "correct", false))))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updating a question replaces its text and options")
    void updatesQuestion() throws Exception {
        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Editable"));
        long questionId = quiz.get("questions").get(0).get("id").asLong();
        long oldOptionId = quiz.get("questions").get(0).get("options").get(0).get("id").asLong();

        mockMvc.perform(json(put("/api/admin/questions/" + questionId),
                        Map.of("text", "10 / 2 = ?", "options", List.of(
                                Map.of("text", "5", "correct", true),
                                Map.of("text", "2", "correct", false),
                                Map.of("text", "20", "correct", false))))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("10 / 2 = ?"))
                .andExpect(jsonPath("$.options.length()").value(3))
                .andExpect(jsonPath("$.options[0].id").value(org.hamcrest.Matchers.not((int) oldOptionId)));
    }

    @Test
    @DisplayName("updating a question keeps the one-correct-option rule and names the question")
    void rejectsAmbiguousQuestionUpdate() throws Exception {
        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Editable"));
        long questionId = quiz.get("questions").get(0).get("id").asLong();

        mockMvc.perform(json(put("/api/admin/questions/" + questionId),
                        Map.of("text", "q", "options", List.of(
                                Map.of("text", "a", "correct", true),
                                Map.of("text", "b", "correct", true))))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Question " + questionId + " must have exactly one correct option (found 2)"));
    }

    @Test
    @DisplayName("updating a question that does not exist is 404")
    void updateMissingQuestion() throws Exception {
        mockMvc.perform(json(put("/api/admin/questions/9999"),
                        Map.of("text", "q", "options", List.of(
                                Map.of("text", "a", "correct", true),
                                Map.of("text", "b", "correct", false))))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Question 9999 not found"));
    }

    @Test
    @DisplayName("a question can be deleted")
    void deletesQuestion() throws Exception {
        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Shrinking"));
        long quizId = quiz.get("id").asLong();
        long questionId = quiz.get("questions").get(0).get("id").asLong();

        mockMvc.perform(delete("/api/admin/questions/" + questionId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/quizzes/" + quizId).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.questions.length()").value(1));
    }
}

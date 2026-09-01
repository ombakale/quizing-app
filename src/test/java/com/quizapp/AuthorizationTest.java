package com.quizapp;

import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationTest extends ApiTestBase {

    @Test
    @DisplayName("protected endpoints need a token")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/api/quizzes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("a garbage token is rejected like no token at all")
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/quizzes").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a student cannot reach the admin endpoints")
    void studentCannotAdminister() throws Exception {
        String studentToken = registerStudent("student");

        mockMvc.perform(json(post("/api/admin/quizzes"), twoQuestionQuiz("Nope"))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(delete("/api/admin/quizzes/1").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can reach the admin endpoints")
    void adminCanAdminister() throws Exception {
        String adminToken = registerAdmin("boss");

        mockMvc.perform(json(post("/api/admin/quizzes"), twoQuestionQuiz("Yes"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("auth and docs endpoints stay public")
    void publicEndpointsStayOpen() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "anyone", "password", "password123")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unmapped API path is 404, not 500")
    void unmappedPathIsNotFound() throws Exception {
        String token = registerStudent("student");

        mockMvc.perform(get("/api/does-not-exist").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unsupported content type is 415, not 500")
    void unsupportedMediaType() throws Exception {
        String adminToken = registerAdmin("boss");

        mockMvc.perform(post("/api/admin/quizzes")
                        .header("Authorization", bearer(adminToken))
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("a non-numeric path variable is a bad request")
    void badPathVariable() throws Exception {
        String token = registerStudent("student");

        mockMvc.perform(get("/api/quizzes/not-a-number").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Parameter 'id' has an invalid value: not-a-number"));
    }

    @Test
    @DisplayName("the answer key never reaches a student, on either read path")
    void answerKeyStaysServerSide() throws Exception {
        String adminToken = registerAdmin("boss");
        String studentToken = registerStudent("student");
        long quizId = createQuiz(adminToken, twoQuestionQuiz("Secrets")).get("id").asLong();

        String list = mockMvc.perform(get("/api/quizzes").header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String detail = mockMvc.perform(get("/api/quizzes/" + quizId)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options[0].text").exists())
                .andReturn().getResponse().getContentAsString();

        assertFalse(list.contains("correct"), "quiz list leaked the answer key: " + list);
        assertFalse(detail.contains("correct"), "quiz detail leaked the answer key: " + detail);
    }

    @Test
    @DisplayName("students only see their own attempt history")
    void attemptsAreScopedToTheUser() throws Exception {
        String adminToken = registerAdmin("boss");
        String alice = registerStudent("alice");
        String bob = registerStudent("bobby");

        com.fasterxml.jackson.databind.JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Shared"));
        long quizId = quiz.get("id").asLong();
        long questionId = quiz.get("questions").get(0).get("id").asLong();
        long optionId = quiz.get("questions").get(0).get("options").get(0).get("id").asLong();

        Map<String, Object> answers = Map.of("answers",
                List.of(Map.of("questionId", questionId, "selectedOptionId", optionId)));

        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"), answers)
                .header("Authorization", bearer(alice))).andExpect(status().isOk());

        mockMvc.perform(get("/api/quizzes/attempts").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/quizzes/attempts").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}

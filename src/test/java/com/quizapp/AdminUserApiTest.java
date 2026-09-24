package com.quizapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User administration. Until this existed the only way to remove an account was to open
 * the database, which is also why the end-to-end suite kept leaving throwaway logins behind.
 */
class AdminUserApiTest extends ApiTestBase {

    @Test
    @DisplayName("an admin can list accounts, without password hashes")
    void listsUsers() throws Exception {
        registerAdmin("boss");
        registerStudent("alice");

        String body = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(registerAdmin("boss2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].username").value("boss"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].username").value("alice"))
                .andExpect(jsonPath("$[1].role").value("USER"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("password"), "user list leaked a password field: " + body);
        assertFalse(body.contains("$2a$"), "user list leaked a bcrypt hash: " + body);
    }

    @Test
    @DisplayName("the list reports how many quizzes each account has submitted")
    void reportsAttemptCounts() throws Exception {
        String adminToken = registerAdmin("boss");
        String studentToken = registerStudent("alice");
        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Counted"));
        submitFirstQuestion(quiz, studentToken);

        JsonNode users = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertEquals(1, attemptCountOf(users, "alice"));
        assertEquals(0, attemptCountOf(users, "boss"));
    }

    @Test
    @DisplayName("deleting an account removes it and its attempt history")
    void deletesUserAndAttempts() throws Exception {
        String adminToken = registerAdmin("boss");
        String studentToken = registerStudent("alice");
        JsonNode quiz = createQuiz(adminToken, twoQuestionQuiz("Doomed"));
        submitFirstQuestion(quiz, studentToken);

        long aliceId = userIdOf(adminToken, "alice");

        mockMvc.perform(delete("/api/admin/users/" + aliceId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("boss"));

        // The token was minted before the delete and is still cryptographically valid, so the
        // filter must notice the account is gone rather than trusting the signature alone.
        mockMvc.perform(get("/api/quizzes").header("Authorization", bearer(studentToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("username", "alice", "password", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the username is free again after the account is deleted")
    void usernameIsReusable() throws Exception {
        String adminToken = registerAdmin("boss");
        registerStudent("alice");

        mockMvc.perform(delete("/api/admin/users/" + userIdOf(adminToken, "alice"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "alice", "password", "password123")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an admin cannot delete their own account")
    void cannotDeleteSelf() throws Exception {
        String adminToken = registerAdmin("boss");

        mockMvc.perform(delete("/api/admin/users/" + userIdOf(adminToken, "boss"))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot delete your own account"));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("deleting an account that is not there is a 404")
    void deleteMissingUser() throws Exception {
        mockMvc.perform(delete("/api/admin/users/9999")
                        .header("Authorization", bearer(registerAdmin("boss"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("a student cannot list or delete accounts")
    void studentsAreShutOut() throws Exception {
        String studentToken = registerStudent("alice");

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/1").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private long attemptCountOf(JsonNode users, String username) {
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("attemptCount").asLong();
            }
        }
        throw new AssertionError("no user named " + username);
    }

    private long userIdOf(String adminToken, String username) throws Exception {
        JsonNode users = objectMapper.readTree(
                mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                        .andReturn().getResponse().getContentAsString());
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new AssertionError("no user named " + username);
    }

    private void submitFirstQuestion(JsonNode quiz, String studentToken) throws Exception {
        long quizId = quiz.get("id").asLong();
        long questionId = quiz.get("questions").get(0).get("id").asLong();
        long optionId = quiz.get("questions").get(0).get("options").get(0).get("id").asLong();

        mockMvc.perform(json(post("/api/quizzes/" + quizId + "/submit"),
                        Map.of("answers", List.of(
                                Map.of("questionId", questionId, "selectedOptionId", optionId))))
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
    }
}

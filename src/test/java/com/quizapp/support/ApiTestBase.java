package com.quizapp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizapp.repository.QuizAttemptRepository;
import com.quizapp.repository.QuizRepository;
import com.quizapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for the API tests: a MockMvc client, a clean database per test, and helpers
 * for the two things every test needs - a token and a quiz to act on.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    protected static final String ADMIN_CODE = "quiz-admin-2026";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected QuizRepository quizRepository;

    @Autowired
    protected QuizAttemptRepository quizAttemptRepository;

    /**
     * The schema is created once for the whole suite (ddl-auto=update on an in-memory H2), so
     * rows are cleared between tests to keep them independent.
     */
    @BeforeEach
    void resetDatabase() {
        quizAttemptRepository.deleteAll();
        quizRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) {
        try {
            return builder.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise test payload", e);
        }
    }

    protected String registerStudent(String username) throws Exception {
        return tokenFrom(Map.of("username", username, "password", "password123"));
    }

    protected String registerAdmin(String username) throws Exception {
        return tokenFrom(Map.of(
                "username", username,
                "password", "password123",
                "role", "ADMIN",
                "adminCode", ADMIN_CODE));
    }

    private String tokenFrom(Map<String, String> payload) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/auth/register"), payload)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** A two-question quiz where the first option of each question is the correct one. */
    protected Map<String, Object> twoQuestionQuiz(String title) {
        return Map.of(
                "title", title,
                "description", "created by tests",
                "questions", java.util.List.of(
                        Map.of("text", "2 + 2 = ?", "options", java.util.List.of(
                                Map.of("text", "4", "correct", true),
                                Map.of("text", "5", "correct", false))),
                        Map.of("text", "3 * 3 = ?", "options", java.util.List.of(
                                Map.of("text", "9", "correct", true),
                                Map.of("text", "6", "correct", false)))));
    }

    /** Creates the quiz through the API and returns the parsed admin-view response. */
    protected com.fasterxml.jackson.databind.JsonNode createQuiz(String adminToken, Map<String, Object> quiz)
            throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/admin/quizzes"), quiz)
                        .header("Authorization", bearer(adminToken)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

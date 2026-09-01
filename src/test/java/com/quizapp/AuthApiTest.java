package com.quizapp;

import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestBase {

    @Test
    @DisplayName("registering without a role creates a student")
    void registersStudent() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "student", "password", "password123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.username").value("student"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("registering as admin requires the registration code")
    void registersAdminWithCode() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "username", "boss", "password", "password123",
                        "role", "ADMIN", "adminCode", ADMIN_CODE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("asking for ADMIN without a code is refused")
    void rejectsAdminWithoutCode() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "username", "sneaky", "password", "password123", "role", "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid admin registration code"));
    }

    @Test
    @DisplayName("asking for ADMIN with the wrong code is refused")
    void rejectsAdminWithWrongCode() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "username", "sneaky", "password", "password123",
                        "role", "ADMIN", "adminCode", "not-the-code")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a taken username returns 409")
    void rejectsDuplicateUsername() throws Exception {
        registerStudent("twice");

        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "twice", "password", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("validation failures list the offending fields")
    void reportsValidationFailures() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "ab", "password", "short", "role", "WIZARD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    @DisplayName("usernames are restricted to safe characters")
    void rejectsOddUsernames() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "drop table users", "password", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    @DisplayName("login returns a token for correct credentials")
    void logsIn() throws Exception {
        registerStudent("returning");

        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("username", "returning", "password", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("a wrong password is 401, not 400")
    void rejectsWrongPassword() throws Exception {
        registerStudent("returning");

        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("username", "returning", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("an unknown user is 401 and reveals nothing extra")
    void rejectsUnknownUser() throws Exception {
        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("username", "ghost", "password", "password123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("a missing password is 401 rather than a 500 out of the encoder")
    void rejectsMissingPassword() throws Exception {
        registerStudent("returning");

        Map<String, String> payload = new HashMap<>();
        payload.put("username", "returning");

        mockMvc.perform(json(post("/api/auth/login"), payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("malformed JSON is reported as a bad request")
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}

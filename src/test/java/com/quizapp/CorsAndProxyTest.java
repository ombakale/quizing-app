package com.quizapp;

import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend ships with the API and is served from the same origin, so it never needs
 * CORS. Everything else does: a swagger editor, an API gateway, a frontend someone runs
 * locally against the deployed API. Without a CORS configuration those callers get a 403
 * on the preflight, because a preflight is an unauthenticated OPTIONS request with no
 * Authorization header on it.
 */
class CorsAndProxyTest extends ApiTestBase {

    private static final String FOREIGN_ORIGIN = "https://editor.swagger.io";

    @Test
    @DisplayName("a preflight from another origin is allowed, not 403")
    void preflightIsAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FOREIGN_ORIGIN));
    }

    @Test
    @DisplayName("a preflight for a protected endpoint is allowed too")
    void preflightOnProtectedEndpoint() throws Exception {
        // The browser sends this before it has attached any token, so it must not be
        // judged by the role rules that guard the real request.
        mockMvc.perform(options("/api/admin/quizzes")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FOREIGN_ORIGIN));
    }

    @Test
    @DisplayName("a real cross-origin call carries the allow-origin header back")
    void actualRequestIsAnnotated() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"),
                        Map.of("username", "cors.user", "password", "password123"))
                        .header("Origin", FOREIGN_ORIGIN))
                .andExpect(status().isCreated())
                .andExpect(header().string("Access-Control-Allow-Origin", FOREIGN_ORIGIN));
    }

    /**
     * Credentials stay off on purpose. Auth is a bearer token the caller attaches itself,
     * so there is nothing ambient for the browser to send, and allowing credentials would
     * rule out the wildcard origin for no gain.
     */
    @Test
    @DisplayName("cookies are not invited along")
    void credentialsAreNotAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", FOREIGN_ORIGIN)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("the API docs are reachable cross-origin, so an external viewer can load them")
    void apiDocsAllowCrossOrigin() throws Exception {
        mockMvc.perform(get("/v3/api-docs").header("Origin", FOREIGN_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FOREIGN_ORIGIN));
    }

    /**
     * Cloud Run terminates TLS and forwards over plain HTTP. If the app ignores
     * X-Forwarded-Proto, springdoc publishes an {@code http://} server URL, and Swagger UI
     * loaded over https then fires Try-it-out calls at a different origin - which the
     * browser reports as a CORS failure. This is the actual root cause of that report.
     */
    @Test
    @DisplayName("behind a TLS-terminating proxy the OpenAPI server URL is https")
    void openApiServerUrlRespectsForwardedProto() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "quiz-api.example.run.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].url").value("https://quiz-api.example.run.app"));
    }
}

package com.quizapp;

import com.quizapp.support.ApiTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frontend is served from the classpath {@code static/} folder by the same JAR that
 * serves the API, so its assets pass through the security filter chain like anything else.
 *
 * <p>The patterns in {@code SecurityConfig.PUBLIC_PATHS} that use a single star match one
 * path segment only, so an asset in a subdirectory is easy to leave behind. When that
 * happens the page still loads but every stylesheet, script and icon under it comes back
 * 401, which presents as a styling bug rather than an auth one. These tests pin the paths
 * the pages actually request.
 */
class StaticFrontendTest extends ApiTestBase {

    /** Every URL the five screens reference, with no Authorization header. */
    @ParameterizedTest(name = "{0} is public")
    @ValueSource(strings = {
            "/",
            "/index.html",
            "/dashboard.html",
            "/quiz.html",
            "/result.html",
            "/admin-quiz-builder.html",
            "/admin-users.html",
            "/app.css",
            "/app.js",
            "/components/navbar.js",
            "/components/option-row.js",
            "/components/quiz-card.js",
            "/components/quiz-composer.js",
            "/components/form-field.js",
            "/components/feedback.js",
            "/assets/brand/quiz-icon.svg",
            "/fonts/space-grotesk-variable.woff2",
            "/favicon.svg",
            "/apple-touch-icon.svg",
            "/site.webmanifest"
    })
    @DisplayName("frontend assets load without a token")
    void assetsAreReachableAnonymously(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    /**
     * The previous frontend hardcoded {@code http://localhost:8080/api}, which cannot work
     * once deployed: {@code server.port} is bound to the {@code PORT} that Cloud Run injects.
     * The API base must stay relative.
     */
    @Test
    @DisplayName("no frontend file hardcodes a host or port")
    void frontendUsesRelativeApiPaths() throws IOException {
        List<String> files = List.of(
                "static/app.js",
                "static/index.html",
                "static/dashboard.html",
                "static/quiz.html",
                "static/result.html",
                "static/admin-quiz-builder.html",
                "static/admin-users.html");

        for (String file : files) {
            String contents = read(file);
            assertFalse(contents.contains("http://localhost"),
                    file + " hardcodes a host; the API base must stay relative");
            assertFalse(contents.contains("127.0.0.1"),
                    file + " hardcodes a host; the API base must stay relative");
        }

        assertTrue(read("static/app.js").contains("const API = '/api'"),
                "app.js should declare a relative API base");
    }

    /**
     * {@code innerHTML} with an API string is an injection path through any admin-authored
     * quiz title, which is exactly what a student's screen renders. Data goes through
     * {@code .textContent}; {@code el()}'s {@code html:} key exists for inline icon markup
     * only, and is used only with values from {@code ICON}.
     */
    @Test
    @DisplayName("no component appends API data with innerHTML")
    void componentsDoNotConcatenateInnerHtml() throws IOException {
        List<String> files = List.of(
                "static/app.js",
                "static/components/navbar.js",
                "static/components/option-row.js",
                "static/components/quiz-card.js",
                "static/components/quiz-composer.js",
                "static/components/form-field.js",
                "static/components/feedback.js",
                "static/index.html",
                "static/dashboard.html",
                "static/quiz.html",
                "static/result.html",
                "static/admin-quiz-builder.html",
                "static/admin-users.html");

        for (String file : files) {
            assertFalse(withoutComments(read(file)).contains("innerHTML +="),
                    file + " builds DOM by appending to innerHTML");
        }
    }

    private String read(String classpathLocation) throws IOException {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Strips comments so the rule above is checked against code rather than against the
     * comments that explain the rule. Crude - it also truncates a line at a {@code //}
     * inside a string literal - which is fine, because the result is only ever scanned for
     * a forbidden pattern, never parsed.
     */
    private String withoutComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }
}

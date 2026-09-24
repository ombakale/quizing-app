package com.quizapp.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * The frontend ships inside this JAR and is served from the classpath {@code static/}
     * folder, which Spring maps to the URL root - not to {@code /static/...}.
     *
     * <p>Note that {@code /*.html} and the other single-star patterns match one path segment
     * only. Every asset that lives in a subdirectory therefore needs its own entry, or it is
     * refused with a 401 and the page renders unstyled with no icons - a failure that looks
     * like a styling bug rather than an auth one.
     */
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/index.html",
            "/static/**",
            "/*.html",
            "/*.css",
            "/*.js",
            "/*.ico",
            "/*.svg",
            "/*.webmanifest",
            "/components/**",
            "/assets/**",
            "/fonts/**",
            "/api/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private RestAuthEntryPoints.JsonAuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private RestAuthEntryPoints.JsonAccessDeniedHandler accessDeniedHandler;

    /**
     * The H2 console is a local development aid only. It is opened up (and the frame-options
     * header relaxed) solely when the console is actually enabled, so a deployed instance with
     * the console switched off never exposes it.
     */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * Origins allowed to call the API from a browser. Comma-separated, and {@code *} means
     * any origin.
     *
     * <p>The bundled frontend is served from this same origin and needs none of this. CORS
     * exists here for the tools that sit outside it: swagger editors, an API gateway, a
     * frontend someone runs locally against the deployed API.
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            // Must come before authorizeHttpRequests takes effect: a CORS preflight is an
            // unauthenticated OPTIONS request with no Authorization header, so without this
            // it is rejected with a 403 and the browser reports it as a CORS failure.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(PUBLIC_PATHS).permitAll();
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                auth.anyRequest().authenticated();
            })
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        if (h2ConsoleEnabled) {
            http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        }

        return http.build();
    }

    /**
     * Authentication here is a bearer token in a header, never a cookie, so credentials are
     * deliberately not allowed: the browser has nothing ambient to send, and switching this
     * on would forbid the {@code *} origin pattern for no benefit.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        // Lets a browser cache the preflight instead of re-sending it before every call.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/v3/api-docs/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

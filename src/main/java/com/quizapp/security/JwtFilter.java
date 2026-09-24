package com.quizapp.security;

import com.quizapp.entity.User;
import com.quizapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * A valid signature is not enough on its own.
     *
     * <p>Tokens here last 24 hours and there is no revocation list, so a token minted before
     * an account was deleted stays cryptographically valid long after the account is gone.
     * Looking the account up on each request is what makes
     * {@code DELETE /api/admin/users/{id}} actually revoke access instead of merely hiding
     * the row. The same lookup means a role change takes effect on the next request rather
     * than whenever the old token happens to expire, because the authority comes from the
     * stored role and not from the claim.
     *
     * <p>The cost is one indexed read per authenticated request. Worth it: the alternative
     * is a deleted account keeping admin rights for the rest of the day.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validateToken(token)) {
                Optional<User> account = userRepository.findByUsername(jwtUtil.extractUsername(token));

                // Absent means the account was deleted after this token was issued. Leaving the
                // context unauthenticated lets the entry point answer 401, exactly as it would
                // for a missing or expired token.
                account.ifPresent(user -> {
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null, Collections.singletonList(authority));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }

        filterChain.doFilter(request, response);
    }
}

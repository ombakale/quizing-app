package com.quizapp.service;

import com.quizapp.dto.AuthRequest;
import com.quizapp.dto.AuthResponse;
import com.quizapp.entity.User;
import com.quizapp.exception.DuplicateResourceException;
import com.quizapp.exception.ForbiddenException;
import com.quizapp.exception.UnauthorizedException;
import com.quizapp.repository.UserRepository;
import com.quizapp.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String adminRegistrationCode;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Value("${app.admin.registration-code}") String adminRegistrationCode) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.adminRegistrationCode = adminRegistrationCode;
    }

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }

        boolean wantsAdmin = request.getRole() != null && request.getRole().equalsIgnoreCase(ROLE_ADMIN);
        if (wantsAdmin && !matchesAdminCode(request.getAdminCode())) {
            throw new ForbiddenException("Invalid admin registration code");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(wantsAdmin ? ROLE_ADMIN : ROLE_USER)
                .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // existsByUsername above is not atomic; the unique constraint is the real guard
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }

        return tokenFor(user);
    }

    public AuthResponse login(AuthRequest request) {
        // Guarded here rather than with @Valid: a missing credential is a 401, not a 400 telling
        // the caller which format rules the stored password happens to break. BCrypt also throws
        // on a null raw password, which would otherwise surface as a 500.
        if (isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return tokenFor(user);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    /**
     * Constant-time comparison so the admin code cannot be guessed by timing the response.
     */
    private boolean matchesAdminCode(String supplied) {
        if (supplied == null || adminRegistrationCode == null || adminRegistrationCode.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                adminRegistrationCode.getBytes(StandardCharsets.UTF_8));
    }
}

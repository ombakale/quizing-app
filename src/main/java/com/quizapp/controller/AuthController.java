package com.quizapp.controller;

import com.quizapp.dto.AuthRequest;
import jakarta.validation.Valid;
import com.quizapp.dto.AuthResponse;
import com.quizapp.entity.User;
import com.quizapp.exception.DuplicateResourceException;
import org.springframework.dao.DataIntegrityViolationException;
import com.quizapp.exception.ForbiddenException;
import com.quizapp.exception.UnauthorizedException;
import com.quizapp.repository.UserRepository;
import com.quizapp.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User Registration and Login")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.admin.registration-code}")
    private String adminRegistrationCode;

    @PostMapping("/register")
    @Operation(summary = "Register a new user, or an admin when a valid adminCode is supplied")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }

        boolean wantsAdmin = request.getRole() != null && request.getRole().equalsIgnoreCase("ADMIN");
        if (wantsAdmin && !matchesAdminCode(request.getAdminCode())) {
            throw new ForbiddenException("Invalid admin registration code");
        }

        String role = wantsAdmin ? "ADMIN" : "USER";

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // existsByUsername above is not atomic; the unique constraint is the real guard
            throw new DuplicateResourceException("Username '" + request.getUsername() + "' is already taken");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, user.getUsername(), user.getRole()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login to get JWT token")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole()));
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

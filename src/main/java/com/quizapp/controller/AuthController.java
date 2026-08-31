package com.quizapp.controller;

import com.quizapp.dto.AuthRequest;
import com.quizapp.dto.AuthResponse;
import com.quizapp.entity.User;
import com.quizapp.repository.UserRepository;
import com.quizapp.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/register")
    @Operation(summary = "Register a new user or admin")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username is already taken");
        }

        String role = (request.getRole() != null && request.getRole().equalsIgnoreCase("ADMIN")) ? "ADMIN" : "USER";

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, user.getUsername(), user.getRole()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login to get JWT token")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getRole()));
    }
}

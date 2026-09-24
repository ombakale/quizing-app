package com.quizapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin-facing view of an account. The password hash is deliberately absent: nothing
 * outside {@code AuthService} has a reason to see it, and a DTO is the cheapest place
 * to make that impossible rather than merely unlikely.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String role;
    /** How many quizzes this account has submitted - shown before deleting it. */
    private long attemptCount;
}

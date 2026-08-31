package com.quizapp.dto;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
    private String role; // "USER" or "ADMIN"
}

# Quiz Application API (Java Spring Boot)

A clean, lightweight, and easy-to-explain REST API for a **Quiz Application** built with **Java 17**, **Spring Boot 3**, **Spring Security + JWT**, and an **H2 Database**.

---

## 📁 Simple Project Architecture

The project is intentionally structured cleanly into 5 core packages:

```
src/main/java/com/quizapp/
├── QuizApplication.java             # Main Entry Point
├── entity/                          # Database Entities (JPA)
│   ├── User.java                    # User table (username, password, role)
│   ├── Quiz.java                    # Quiz table (title, description, questions)
│   ├── Question.java                # Question table (text, options)
│   ├── Option.java                  # Option table (text, correctness flag)
│   └── QuizAttempt.java             # Attempts history table (score, percentage)
├── repository/                      # JPA Repositories (Spring Data JPA)
│   ├── UserRepository.java
│   ├── QuizRepository.java
│   ├── QuestionRepository.java
│   └── QuizAttemptRepository.java
├── security/                        # Authentication Logic
│   ├── JwtUtil.java                 # Generates and validates JWT tokens
│   ├── JwtFilter.java               # Intercepts HTTP requests to extract Bearer token
│   └── SecurityConfig.java          # Security filter chain (permits /api/auth, locks /api/admin)
├── dto/                             # Data Transfer Objects
│   ├── AuthRequest.java             # Login/Registration body
│   ├── AuthResponse.java            # JWT Token response
│   ├── QuizSubmitRequest.java       # Answers payload
│   └── QuizResultResponse.java      # Calculated score & percentage payload
└── controller/                      # REST API Endpoints
    ├── AuthController.java          # /api/auth/register & /api/auth/login
    ├── AdminController.java         # /api/admin/quizzes (Create/Edit/Delete Quiz & Questions)
    └── QuizController.java          # /api/quizzes (View Quiz, Submit Answers, View Scores)
```

---

## 🔑 Key Concepts to Explain in your Interview

### 1. Authentication & Security (`security/`)
- **BCrypt Password Encoder**: Hashes passwords securely before storing in the database.
- **JWT (JSON Web Token)**: Stateless authentication. Upon logging in, the server signs a token with a secret key containing the `username` and `role`.
- **JwtFilter**: Intercepts every incoming request, reads `Authorization: Bearer <token>`, validates the token signature, and sets the authenticated user in `SecurityContextHolder`.

### 2. Admin vs User Permissions (`SecurityConfig.java`)
- `/api/auth/**`: Publicly accessible.
- `/api/admin/**`: Restricted to users with `ROLE_ADMIN`.
- `/api/quizzes/**`: Accessible to any logged-in user.

### 3. Quiz Submission & Automatic Scoring (`QuizController.java`)
- When a student requests a quiz (`GET /api/quizzes/{id}`), the server **strips out the `correct` boolean** from option objects so students cannot see answer keys in inspect/dev tools.
- When a student submits answers (`POST /api/quizzes/{id}/submit`):
  1. The server maps the student's selected `optionId` per `questionId`.
  2. Compares the selected option against the stored `correct` option.
  3. Increments the `score` count if correct.
  4. Calculates `percentage = (score / totalQuestions) * 100`.
  5. Saves a `QuizAttempt` record into the database and returns the result breakdown.

---

## 🚀 How to Run the Application

### Prerequisites
- **Java 17+** installed.
- **Maven** installed.

### Run Commands
```bash
cd "c:/Users/bakal/Desktop/quizing app"
mvn clean package
mvn spring-boot:run
```

### Web User Interface (Frontend)
Open your browser to:
👉 **[http://localhost:8080/](http://localhost:8080/)**

- **Register / Login**: Register as Student (`USER`) or `ADMIN`.
- **Admin**: Easily create new quizzes with questions & correct options.
- **Student**: View available quizzes, select answers, submit, and instantly see your score & percentage!

---

### Interactive Swagger UI
Once running, open your browser to:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### H2 Database Console
To inspect the database in your browser:
👉 **[http://localhost:8080/h2-console](http://localhost:8080/h2-console)**
- **JDBC URL**: `jdbc:h2:mem:quizdb`
- **User**: `sa`
- **Password**: `password`

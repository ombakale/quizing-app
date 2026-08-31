# Quiz Application API

A RESTful Quiz Application API built with **Java 17**, **Spring Boot 3**, **Spring Security + JWT**, and **H2 In-Memory Database** (production-ready for PostgreSQL).

---

## Features

- **User Authentication & Authorization**:
  - JWT-based authentication.
  - Role-Based Access Control (`ROLE_USER` and `ROLE_ADMIN`).
  - User Registration & Login endpoints.
- **Admin Management**:
  - Create, update, and delete quizzes (`/api/admin/quizzes`).
  - Add, update, and delete questions & options under quizzes (`/api/admin/questions`).
- **Student / User Quiz Execution**:
  - Browse available quizzes (`GET /api/quizzes`).
  - View quiz questions & options without revealing answer keys (`GET /api/quizzes/{id}`).
  - Submit answers, automatically calculate scores, and store attempt details (`POST /api/quizzes/{id}/submit`).
  - View attempt history (`GET /api/quizzes/attempts`).
- **Validation & Error Handling**:
  - Global `@RestControllerAdvice` returning structured error responses.
  - Bean validation on request payloads.
- **API Documentation**:
  - OpenAPI 3.0 / Swagger UI integrated (`/swagger-ui.html`).

---

## API Endpoints Overview

### Authentication (`/api/auth`)
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| `POST` | `/api/auth/register` | Register a new user (`ROLE_USER` or `ROLE_ADMIN`) | Public |
| `POST` | `/api/auth/login` | Authenticate user & get JWT token | Public |

### Admin Quiz Management (`/api/admin`)
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| `POST` | `/api/admin/quizzes` | Create a new quiz | ADMIN |
| `PUT` | `/api/admin/quizzes/{id}` | Update quiz info | ADMIN |
| `DELETE` | `/api/admin/quizzes/{id}` | Delete a quiz | ADMIN |
| `POST` | `/api/admin/quizzes/{quizId}/questions` | Add question to quiz | ADMIN |
| `PUT` | `/api/admin/questions/{questionId}` | Update question & options | ADMIN |
| `DELETE` | `/api/admin/questions/{questionId}` | Delete question | ADMIN |

### Student Quizzes (`/api/quizzes`)
| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| `GET` | `/api/quizzes` | List all available quizzes | Authenticated |
| `GET` | `/api/quizzes/{id}` | Fetch quiz questions (without correct answers) | Authenticated |
| `POST` | `/api/quizzes/{id}/submit` | Submit answers & receive score | Authenticated |
| `GET` | `/api/quizzes/attempts` | View user's previous attempts | Authenticated |

---

## Running the Application Locally

### Prerequisites
- **Java 17** or later installed (`java -version`).
- **Maven** installed (`mvn -version`).

### Build & Run
1. Clone / Navigate to project folder:
   ```bash
   cd "c:/Users/bakal/Desktop/quizing app"
   ```
2. Build the project:
   ```bash
   mvn clean package
   ```
3. Run the application:
   ```bash
   mvn spring-boot:run
   ```
4. Access Swagger Documentation & UI:
   - [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
5. Access H2 Console:
   - [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
   - JDBC URL: `jdbc:h2:mem:quizdb`
   - User: `sa`
   - Password: `password`

---

## Quick Start / Testing Flow

1. **Register Admin**:
   `POST /api/auth/register`
   ```json
   {
     "username": "admin",
     "password": "adminpassword",
     "role": "ADMIN"
   }
   ```
2. **Login Admin**:
   `POST /api/auth/login` to obtain `token`.
3. **Authorize in Swagger UI**:
   Click `Authorize` button at the top right of Swagger UI and paste `Bearer <token>`.
4. **Create Quiz** as Admin:
   `POST /api/admin/quizzes`
   ```json
   {
     "title": "Java Fundamentals",
     "description": "Test your core Java knowledge",
     "questions": [
       {
         "text": "Which keyword is used to define a class in Java?",
         "options": [
           { "text": "class", "correct": true },
           { "text": "struct", "correct": false },
           { "text": "interface", "correct": false }
         ]
       }
     ]
   }
   ```
5. **Register & Login Student**:
   Register with `role: "USER"`, log in, and use the student's JWT token.
6. **Take Quiz & Submit**:
   `POST /api/quizzes/1/submit` with student JWT:
   ```json
   {
     "answers": [
       {
         "questionId": 1,
         "selectedOptionId": 1
       }
     ]
   }
   ```

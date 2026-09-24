# Quiz Application API (Java Spring Boot)

A clean, lightweight, and easy-to-explain REST API for a **Quiz Application** built with **Java 17**, **Spring Boot 3**, **Spring Security + JWT**, and an **H2 Database**.

---

## 🌐 Deployed API (GCP Cloud Run)

**Base URL:** https://quiz-api-t5xplsyivq-el.a.run.app

| Resource | URL |
|---|---|
| Swagger UI | https://quiz-api-t5xplsyivq-el.a.run.app/swagger-ui.html |
| OpenAPI JSON | https://quiz-api-t5xplsyivq-el.a.run.app/v3/api-docs |
| Web UI | https://quiz-api-t5xplsyivq-el.a.run.app/ |

**Sample data** — two quizzes are already loaded: *Python Fundamentals* and *Java
Fundamentals*, five questions each.

| Role | Username | Password |
| --- | --- | --- |
| Student | `demo.student` | `DemoStudent2026` |
| Admin | `quizadmin` | shared with reviewers separately |

The admin password is deliberately not in this public repository: an admin can delete
every quiz and every account on the live deployment. Anyone can register their own
Student account from the login screen; registering an Admin needs the registration code,
which is also shared separately.

Usernames are matched without regard to case, so `demo.student` and `Demo.Student` are
the same account. Passwords are case-sensitive.

Deployment details:

- **Project:** `quiz-app-507418` · **Region:** `asia-south1` (Mumbai) · **Service:** `quiz-api`
- Built straight from the `Dockerfile` with `gcloud run deploy --source .`
- Runs with `SPRING_PROFILES_ACTIVE=prod`: the H2 console is off, and the JWT secret, admin
  code and database credentials all come from the environment rather than dev defaults.
- **Postgres, not H2.** See [Database](#-database). This is what makes a registration
  survive a restart, and it is why `--max-instances` is no longer pinned to 1.

Redeploy after a code change:

```bash
gcloud run deploy quiz-api --source . --region asia-south1 --project quiz-app-507418
```

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

## 🗄️ Database

| | Local & tests | Deployed |
| --- | --- | --- |
| Engine | H2, in memory | PostgreSQL 17 (Supabase) |
| Config | `application.yml` | `application-prod.yml` |
| Schema | Hibernate `ddl-auto: update` | Hibernate `ddl-auto: update` |
| Lifetime | cleared on restart | persistent |

Tables: `users`, `quizzes`, `questions`, `options`, `quiz_attempts`.

One piece of schema Hibernate cannot express lives in `schema-postgresql.sql`: a unique
index on `lower(username)`. The service refuses a username that differs from an existing
one only by case, but that check and the insert are two statements, so two racing
registrations could both pass it. The index makes Postgres refuse the second one. It runs
after Hibernate (`spring.jpa.defer-datasource-initialization`) and against Postgres only.

### Why this changed

The deployed app originally ran on the same in-memory H2 as local development. On
Cloud Run that database lives inside the container, so **every cold start silently
wiped every user and quiz.** Three separate bug reports were all this one cause:

- *"Login fails after registration."* The account was real when it was created, then
  the instance scaled to zero and the account went with it.
- *"Duplicate registration is permitted."* `existsByUsername` was working correctly —
  the earlier user had simply ceased to exist, so the name was free again.
- *"No sample quiz data."* Seeded quizzes disappeared the same way.

Verified fixed: registering, restarting the container, and logging back in now
returns `200`, and re-registering the same username returns `409`.

### Connection

Three environment variables, no credentials in the repo:

```
DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.<project-ref>
DB_PASSWORD=<from Secret Manager>
```

The password is held in GCP Secret Manager as `quiz-db-password` and mounted by
Cloud Run with `--set-secrets DB_PASSWORD=quiz-db-password:latest`, so it never
appears in the service config, the repo or a shell history.

Two things about the host are load-bearing:

- **Use the pooler host, not `db.<ref>.supabase.co`.** The direct host resolves to
  IPv6 only and Cloud Run egress is IPv4, so it simply cannot be reached.
- **Port 5432, not 6543.** 5432 is the pooler's session mode, which supports the
  prepared statements Hibernate uses; 6543 is transaction mode, which does not.

The database is in Singapore while Cloud Run is in Mumbai, which costs roughly 50 ms
per round trip. Supabase fixes a project's region at creation, so moving it would
mean creating a new project.

Hikari is deliberately capped small (`DB_POOL_SIZE`, default 5) because the pooler
counts every connection against the project's budget and several Cloud Run instances
share it. Connections are recycled every 5 minutes, since Cloud Run freezes idle
instances and a long-lived connection is usually dead by the time it is reused.

---

## 🎨 Frontend

Six screens served as static files from the same JAR as the API.

```
src/main/resources/static/
├── index.html                  Login / Register — the entry point
├── dashboard.html              Available Quizzes + attempt history
├── quiz.html                   Taking a quiz
├── result.html                 Score and per-question review
├── admin-quiz-builder.html     Create or edit a quiz (Admin only)
├── admin-users.html            List and delete accounts (Admin only)
├── app.css                     Design system, compiled (see below)
├── app.js                      API client, session, validation, DOM helpers
├── components/                 Reusable factories, plain functions returning DOM nodes
│   ├── navbar.js                 Navbar with username + role badge
│   ├── option-row.js             OptionRow, ReviewOptionRow — the core control
│   ├── quiz-card.js              QuizCard, EmptyState
│   ├── quiz-composer.js          Repeatable questions/options for the admin screen
│   ├── form-field.js             The two branches of the API's ErrorResponse
│   └── feedback.js               Toast, progress bar
├── assets/brand/               Icon and wordmark SVGs
├── fonts/                      Space Grotesk (self-hosted, 22 KB)
└── favicon.svg, apple-touch-icon.svg, site.webmanifest
```

### Why no framework or build step

Plain HTML, CSS and vanilla JavaScript. No bundler, no npm, no build step.

- **Same-origin, so no CORS.** There is no `CorsConfigurationSource` bean in this
  project. A separate dev server on another port would be blocked outright; serving
  the frontend from Spring Boot's `static/` folder sidesteps the problem instead of
  working around it. If you ever move to React, add the CORS bean *first*.
- **One artifact.** `mvn package` produces a single JAR that serves the API, the
  frontend and the Swagger UI. One `gcloud run deploy` ships all three.
- **The API base is relative** — `const API = '/api'` in `app.js`. `server.port` is
  bound to the `PORT` that Cloud Run injects, so a hardcoded host or port breaks the
  moment it is deployed. `StaticFrontendTest` fails the build if one reappears.

### How the screens talk to the API

| Screen | Calls |
| --- | --- |
| `index.html` | `POST /api/auth/register`, `POST /api/auth/login` |
| `dashboard.html` | `GET /api/quizzes`, `GET /api/quizzes/attempts` |
| `quiz.html` | `GET /api/quizzes/{id}`, `POST /api/quizzes/{id}/submit` |
| `result.html` | none — renders the submit response |
| `admin-quiz-builder.html` | `GET`/`POST`/`PUT /api/admin/quizzes`, `POST`/`PUT`/`DELETE` on questions |
| `admin-users.html` | `GET /api/admin/users`, `DELETE /api/admin/users/{id}` |

The JWT lives in `localStorage`; every call except `/api/auth/**` sends
`Authorization: Bearer <token>`. Sessions are stateless and CSRF is disabled, so
there is no cookie and no CSRF token. `jwt.expiration` is 24 h and there is no
refresh endpoint, so an expired token surfaces as a `401`, which clears the stored
session and returns to the login screen.

An in-flight attempt (quiz id plus answers so far) is held in `sessionStorage`, not
`localStorage` — it belongs to one tab and must not outlive the browser session.

### Error handling — one shape, two branches

Every failure returns the same `ErrorResponse`. `fieldErrors` is `NON_NULL`, so it
appears only on validation failures:

- **`fieldErrors` present** → rendered under the matching input, with
  `aria-invalid="true"` and `aria-describedby` pointing at the message. Nested paths
  from Bean Validation (`questions[0].options[1].text`) are routed back to the exact
  input they came from.
- **Otherwise** → the server's `message` in an `.alert-danger` with `role="alert"`.

The message shown is always the server's own. Client-side rules mirror the server's
Bean Validation limits exactly so the first feedback is not a round-trip `400` — with
one deliberate exception: the admin registration code is a server secret, so the
client only checks that something was entered and lets the server return `403`.

### The answer key never reaches the browser early

`GET /api/quizzes/{id}` returns `StudentQuizResponse`, whose option type has no
`correct` field at all. The frontend never reconstructs or caches correctness before
submission: the review states on `result.html` are the join of `details[]` from the
submit response (which carries `correctOptionId`) with the option *text* from the
quiz payload. All API-supplied text is set with `.textContent`, never `innerHTML` —
an admin-authored quiz title is untrusted input on a student's screen.

### Design system

`app.css` is the Quiz Application design system compiled in dependency order — `colors_and_type.css`, then `tokens.css`, `components.css` and
`kit.css` — followed by a short, clearly-marked app-local block. Every colour, size,
radius and duration is an OKLch token; no raw values. Two things in that local block
are worth knowing about:

- `[hidden] { display: none !important }`. `.alert` and `.error` both set
  `display: flex`, and an author rule beats the browser's own `[hidden]` rule, so
  without this every collapsed alert paints as an empty coloured box while
  `element.hidden` still reads `true`.
- `.option-correct:has(input:checked)` and `.option-wrong:has(input:checked)`. A
  review row keeps its radio checked to show what the student picked, so it also
  matches `.option:has(input:checked)`, which has higher specificity than the plain
  review classes. Left alone, every review row paints accent blue and the
  correct/incorrect distinction disappears.

To re-compile after a design system change, concatenate the four sheets in that
order, point the `@font-face` `url()` at `/fonts/`, then re-append the local block.

### What an admin can do from the UI

Originally the UI could only create a quiz; everything else existed in the API but had
no way to reach it. All of it is wired up now:

| Action | Where | Calls |
| --- | --- | --- |
| Create a quiz | **Create Quiz** on the dashboard | `POST /api/admin/quizzes` |
| Edit a quiz | **Edit** on a quiz tile | `admin-quiz-builder.html?id=<quiz>` |
| Delete a quiz | **Delete** on a quiz tile | `DELETE /api/admin/quizzes/{id}` |
| List accounts | **Manage Users** | `GET /api/admin/users` |
| Delete an account | **Delete** on a user row | `DELETE /api/admin/users/{id}` |

**Editing is a reconciliation, not a replace.** There is no "save the whole quiz" call,
so the builder works out the difference: the title and description go through
`PUT /api/admin/quizzes/{id}`, a question that already has a server id is a `PUT`, one
the author added is a `POST`, and one they removed is a `DELETE`. Deletes run last, so
a failure partway through never leaves the quiz shorter than intended.

The edit screen reads `GET /api/admin/quizzes/{id}` rather than the student endpoint,
because it needs the answer key to show which option is currently marked correct — and
the student response type has no `correct` field at all.

**Deleting asks first, inline.** Not `window.confirm()`: that is the blocking dialog
this project already removed for success messages, it cannot be styled, and it never
gets the design system's focus ring. The row's buttons are replaced by a question with
Cancel and Delete, Cancel takes focus, and Escape backs out.

**An admin cannot delete their own account.** It would invalidate the token
mid-request, and if they were the last admin it would leave nobody able to author
anything. The server rejects it with a `400` and the UI does not offer the button.

### Deleting an account actually revokes access

Tokens last 24 hours and there is no revocation list, so a token minted before an
account was deleted stays cryptographically valid long after the account is gone.
`JwtFilter` used to accept any correctly-signed token, which meant a deleted user kept
working — including a deleted admin keeping admin rights for the rest of the day.

The filter now looks the account up on every authenticated request and takes the
authority from the **stored** role rather than the token claim. So a delete takes
effect immediately, and a role change takes effect on the next request. The cost is
one indexed read per authenticated request, which is the right trade.

### CORS

The bundled frontend is same-origin and needs no CORS at all. The configuration in
`SecurityConfig` exists for everyone else: swagger editors, an API gateway, a
frontend someone runs locally against the deployed API.

`app.cors.allowed-origins` (env `APP_CORS_ALLOWED_ORIGINS`) is a comma-separated
list and defaults to `*`. Credentials are deliberately **not** allowed: auth is a
bearer token the caller attaches itself, so there is nothing ambient for the browser
to send, and allowing credentials would rule out the wildcard origin for no gain.

Preflights are handled before the authorization rules. A preflight is an
unauthenticated `OPTIONS` with no `Authorization` header, so judging it by the role
rules that guard the real request returned `403` — and the browser reported that as
a CORS failure.

#### The Swagger "CORS error" was really a scheme problem

Cloud Run terminates TLS at its front end and forwards to the container over plain
HTTP. Spring therefore believed the request scheme was `http`, and springdoc
published this:

```json
"servers": [{ "url": "http://quiz-api-....a.run.app" }]
```

Swagger UI is loaded over `https`, so every Try-it-out fired at a *different origin*
— and the browser blocked it. `server.forward-headers-strategy: framework` makes
Spring honour `X-Forwarded-Proto`, and the published URL is now `https`. It is safe
here because every request arrives through Google's front end, which sets that
header itself; do not enable it where the app is directly reachable.

Verified in a real browser: Swagger UI's Try-it-out on `POST /api/auth/login` now
issues `https://.../api/auth/login`, returns a token, and logs no CORS or
mixed-content errors.

### One backend change the frontend needed

`SecurityConfig.PUBLIC_PATHS` used single-star patterns (`/*.html`, `/*.css`,
`/*.js`), which match **one path segment only**. Classpath `static/` content is
served at the URL root, not under `/static/`, so `/components/navbar.js`,
`/assets/brand/quiz-icon.svg` and `/fonts/space-grotesk-variable.woff2` were all
refused with a `401` — a failure that looks like a styling bug rather than an auth
one. `/components/**`, `/assets/**`, `/fonts/**`, `/*.svg` and `/*.webmanifest` were
added, and `StaticFrontendTest` now pins every URL the five screens request.

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

## 🧪 Automated Tests

```bash
mvn test
```

94 tests, all `@SpringBootTest` + `MockMvc` against a clean in-memory H2 per test:

| Class | Covers |
| --- | --- |
| `AuthApiTest` | registration, login, duplicate usernames, case-insensitive usernames, the admin code, validation |
| `AuthorizationTest` | tokens, role gating, the error shape for 401/403/404/415, answer-key leakage, per-user attempt scoping |
| `AdminQuizApiTest` | quiz and question CRUD, reading the answer key, nested validation |
| `AdminUserApiTest` | listing accounts without password hashes, attempt counts, deleting an account and its history, the self-delete guard, revoking a deleted user's token |
| `QuizSubmissionTest` | scoring, percentages, skipped answers, attempt history |
| `StaticFrontendTest` | every frontend URL loads without a token; no hardcoded host; no `innerHTML +=` with API data |
| `CorsAndProxyTest` | preflights are allowed on public and protected paths; credentials stay off; the OpenAPI server URL is https behind a TLS-terminating proxy |

### Frontend end-to-end checks

```bash
mvn spring-boot:run          # in one terminal
./e2e/run.sh                 # in another
```

79 checks that drive a real headless browser through all six screens — auth,
role gating, authoring, taking a quiz, the review states, attempt history, focus
rings, reduced motion and a 375px viewport — plus editing and deleting a quiz and
deleting an account, all through the UI. No npm install; see
[`e2e/README.md`](e2e/README.md). Point it at the deployment with
`BASE=... ADMIN_CODE=... ./e2e/run.sh`.

---

## 🚀 How to Run the Application

### Prerequisites
- **Java 17+** installed.
- **Maven** installed.

### Run Commands
```bash
mvn clean package
mvn spring-boot:run
```

That starts on in-memory H2 — no database to install, and the data resets on every
restart, which is what you want locally.

To run locally against the real Postgres instead (useful for reproducing a
deployment issue), set the prod profile and the three datasource variables:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL='jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require'
export DB_USERNAME='postgres.<project-ref>'
export DB_PASSWORD='<password>'
export JWT_SECRET='<32+ bytes>'
export ADMIN_REGISTRATION_CODE='<your code>'
java -jar target/quiz-api-1.0.0.jar
```

The prod profile has no defaults for any of these, so a missing one fails at startup
rather than quietly falling back to a throwaway in-memory database.

### Web User Interface (Frontend)
Open your browser to:
👉 **[http://localhost:8080/](http://localhost:8080/)**

- **Login / Register** — register as a Student, or as an Admin with the registration code.
- **Admin** — author quizzes with questions and exactly one correct option each.
- **Student** — take a quiz, submit, and see the score *and* a per-question review.

The frontend is covered in detail under [Frontend](#-frontend) below.

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

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer so source edits do not re-download them
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Unprivileged user: nothing in the app needs root
RUN groupadd --system quizapp && useradd --system --gid quizapp --home /app quizapp

COPY --from=build /build/target/quiz-api-1.0.0.jar app.jar
RUN chown quizapp:quizapp /app/app.jar
USER quizapp

# Cloud Run injects PORT; application.yml falls back to 8080 for local runs
EXPOSE 8080
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

package com.quizapp.repository;

import com.quizapp.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * Newest attempt first. attemptedAt is assigned in Java, so two attempts can share a
     * timestamp; the id tiebreak keeps the order stable.
     */
    List<QuizAttempt> findByUserIdOrderByAttemptedAtDescIdDesc(Long userId);

    /**
     * Detaches attempts from a quiz that is about to be deleted, so the history survives
     * without the foreign key blocking the delete.
     */
    @Modifying
    @Query("update QuizAttempt a set a.quiz = null where a.quiz.id = :quizId")
    int detachFromQuiz(@Param("quizId") Long quizId);
}

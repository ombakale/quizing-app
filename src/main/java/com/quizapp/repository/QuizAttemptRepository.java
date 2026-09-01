package com.quizapp.repository;

import com.quizapp.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** Newest attempt first, which is the order the history is read in. */
    List<QuizAttempt> findByUserIdOrderByAttemptedAtDesc(Long userId);

    /**
     * Detaches attempts from a quiz that is about to be deleted, so the history survives
     * without the foreign key blocking the delete.
     */
    @Modifying
    @Query("update QuizAttempt a set a.quiz = null where a.quiz.id = :quizId")
    int detachFromQuiz(@Param("quizId") Long quizId);
}

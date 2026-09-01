package com.quizapp.repository;

import com.quizapp.dto.response.QuizSummaryResponse;
import com.quizapp.entity.Quiz;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    /**
     * Projects straight into the summary DTO. Mapping entities here instead would lazily load
     * every quiz's questions just to count them - one extra SELECT per row.
     */
    @Query("""
            select new com.quizapp.dto.response.QuizSummaryResponse(q.id, q.title, q.description, size(q.questions))
            from Quiz q
            order by q.id
            """)
    List<QuizSummaryResponse> findAllSummaries();
}

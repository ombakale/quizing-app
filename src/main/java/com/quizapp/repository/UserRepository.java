package com.quizapp.repository;

import com.quizapp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    /** Exact match. Only for a username that is already canonical, such as a token's subject. */
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * What a person typing their username should hit. "Admin-om" and "admin-om" are the same
     * account: matching case-sensitively made a returning user's correct credentials fail
     * with "Invalid username or password", and let them register the other casing as a
     * second, empty account - which looked exactly like the app having forgotten them.
     */
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<User> findAllByOrderByIdAsc();
}

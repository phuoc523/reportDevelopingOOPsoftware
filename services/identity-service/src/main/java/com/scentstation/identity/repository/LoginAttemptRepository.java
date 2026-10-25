package com.scentstation.identity.repository;

import com.scentstation.identity.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {
    List<LoginAttempt> findByAttemptedUsernameOrderByAttemptedAtDesc(String attemptedUsername);
}

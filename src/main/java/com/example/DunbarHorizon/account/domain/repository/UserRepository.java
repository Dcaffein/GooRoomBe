package com.example.DunbarHorizon.account.domain.repository;

import com.example.DunbarHorizon.account.domain.User;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    User save(User user);
    Optional<User> findById(Long id);
    List<User> findActivatedUsers(Collection<Long> ids);
    Optional<User> findActivatedUser(Long id);

    void delete(User existingUser);

    void flush();
}
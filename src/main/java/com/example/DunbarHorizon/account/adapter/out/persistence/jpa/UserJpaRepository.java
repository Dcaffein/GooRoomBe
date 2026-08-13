package com.example.DunbarHorizon.account.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.account.domain.User;
import com.example.DunbarHorizon.account.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findAllByIdInAndStatus(Collection<Long> ids, UserStatus status);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);

}

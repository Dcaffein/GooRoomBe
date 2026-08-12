package com.example.DunbarHorizon.account.adapter.out.persistence.jpa;

import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuthJpaRepository extends JpaRepository<Auth, Long> {
    Optional<Auth> findByUserIdAndProvider(Long userId, AuthProvider provider);
    boolean existsByUserIdAndProviderAndProviderId(Long userId, AuthProvider provider, String providerId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Auth a WHERE a.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}

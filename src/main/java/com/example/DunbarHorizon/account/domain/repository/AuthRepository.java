package com.example.DunbarHorizon.account.domain.repository;

import com.example.DunbarHorizon.account.domain.Auth;
import com.example.DunbarHorizon.account.domain.AuthProvider;

import java.util.Optional;

public interface AuthRepository {
    Optional<Auth> findByUserIdAndProvider(Long userId, AuthProvider provider);
    Auth save(Auth auth);
    boolean existsByUserIdAndProviderAndProviderId(Long userId, AuthProvider provider, String providerId);

    void deleteAllByUserId(Long userId);
}
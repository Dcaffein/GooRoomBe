package com.example.GooRoomBe.account.auth.repository;

import com.example.GooRoomBe.account.auth.domain.OAuth;
import com.example.GooRoomBe.account.auth.domain.Provider;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface OAuthRepository extends Neo4jRepository<OAuth, String> {
    Optional<OAuth> findByProviderAndProviderId(Provider provider, String providerId);
}
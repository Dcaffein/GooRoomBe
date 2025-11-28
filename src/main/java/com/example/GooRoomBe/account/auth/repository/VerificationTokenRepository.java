package com.example.GooRoomBe.account.auth.repository;

import com.example.GooRoomBe.account.auth.domain.token.VerificationToken;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import java.util.Optional;

public interface VerificationTokenRepository extends Neo4jRepository<VerificationToken, String> {
    Optional<VerificationToken> findByTokenValue(String tokenValue);
    Optional<VerificationToken> findByUser_Id(String userId);
}
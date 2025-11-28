package com.example.GooRoomBe.account.auth.repository;

import com.example.GooRoomBe.account.auth.domain.token.RefreshToken;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends Neo4jRepository<RefreshToken, String> {
    Optional<RefreshToken> findByUser_Id(String userId);
    void deleteAllByUserId(String userId);
    void deleteByUserId(String userId);
}

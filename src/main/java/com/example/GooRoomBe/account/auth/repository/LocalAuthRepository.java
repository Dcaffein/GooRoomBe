package com.example.GooRoomBe.account.auth.repository;

import com.example.GooRoomBe.account.auth.domain.LocalAuth;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocalAuthRepository extends Neo4jRepository<LocalAuth, String> {
    @Query("MATCH (localAuth:LocalAuth)-[r:GUARANTEES]->(user:User {email:$email}) " +
            "RETURN localAuth, r, user")
    Optional<LocalAuth> findByEmail(String email);
}

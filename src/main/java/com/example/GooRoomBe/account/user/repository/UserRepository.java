package com.example.GooRoomBe.account.user.repository;

import com.example.GooRoomBe.account.user.domain.User;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface UserRepository extends Neo4jRepository<User, String> {
    Optional<User> findByEmail(String email);
}

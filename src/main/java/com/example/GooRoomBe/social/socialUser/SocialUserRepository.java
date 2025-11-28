package com.example.GooRoomBe.social.socialUser;

import com.example.GooRoomBe.social.common.exception.SocialUserNotFoundException;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SocialUserRepository extends Neo4jRepository<SocialUser, String> {
    default SocialUser getUser(String userId) {
        return findById(userId).orElseThrow(()-> new SocialUserNotFoundException(userId));
    }
}
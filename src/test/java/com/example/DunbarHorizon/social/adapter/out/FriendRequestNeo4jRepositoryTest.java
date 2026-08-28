package com.example.DunbarHorizon.social.adapter.out;

import com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.springData.FriendRequestNeo4jRepository;
import com.example.DunbarHorizon.social.adapter.out.persistence.neo4j.springData.SocialUserNeo4jRepository;
import com.example.DunbarHorizon.social.domain.friend.FriendRequest;
import com.example.DunbarHorizon.social.domain.friend.FriendRequestStatus;
import java.time.LocalDateTime;
import com.example.DunbarHorizon.social.domain.friend.FriendTestFactory;
import com.example.DunbarHorizon.social.domain.socialUser.SocialUser;
import com.example.DunbarHorizon.support.Neo4jRepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.assertj.core.api.Assertions.assertThat;

@Neo4jRepositoryTest
class FriendRequestNeo4jRepositoryTest {

    @Autowired
    private FriendRequestNeo4jRepository friendRequestRepository;

    @Autowired
    private SocialUserNeo4jRepository socialUserNeo4jRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    private SocialUser requester;
    private SocialUser receiver;

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        requester = socialUserNeo4jRepository.save(new SocialUser(1L, "요청자", "url1"));
        receiver = socialUserNeo4jRepository.save(new SocialUser(2L, "수신자", "url2"));
    }

    @Test
    @DisplayName("mergeFriendRequest를 통해 새로운 요청 노드와 관계를 생성한다")
    void saveRequest_Create_Success() {
        FriendRequest newRequest = FriendTestFactory.createRequest(requester, receiver);

        // when
        FriendRequest saved = friendRequestRepository.mergeFriendRequest(
                requester.getId(), receiver.getId(), newRequest.getId(), LocalDateTime.now());

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(FriendRequestStatus.PENDING);

        // 실제 DB에 관계가 형성되었는지 확인
        boolean exists = friendRequestRepository.existsRequestBetween(requester.getId(), receiver.getId());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 요청의 상태를 수락(ACCEPTED)으로 업데이트한다")
    void saveRequest_Update_Success() {
        // given
        FriendRequest newRequest = FriendTestFactory.createRequest(requester, receiver);
        friendRequestRepository.mergeFriendRequest(
                requester.getId(), receiver.getId(), newRequest.getId(), LocalDateTime.now());

        // findById로 관계(receiver 등)가 함께 로드된 엔티티를 가져옴
        FriendRequest saved = friendRequestRepository.findById(newRequest.getId())
                .orElseThrow(() -> new AssertionError("저장된 요청을 찾을 수 없습니다."));

        // 도메인 행위 호출
        saved.updateStatus(receiver.getId(), FriendRequestStatus.ACCEPTED);

        // when
        friendRequestRepository.updateFriendRequest(saved);

        // then
        FriendRequest found = friendRequestRepository.findById(saved.getId())
                .orElseThrow(() -> new AssertionError("저장된 요청을 찾을 수 없습니다."));

        assertThat(found.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("요청의 방향성을 정확히 인식한다 (A->B 존재 시 B->A는 false)")
    void existsRequestBetween_Directional_Check() {
        // given
        FriendRequest newRequest = FriendTestFactory.createRequest(requester, receiver);
        friendRequestRepository.mergeFriendRequest(
                requester.getId(), receiver.getId(), newRequest.getId(), LocalDateTime.now());

        // when
        boolean forward = friendRequestRepository.existsRequestBetween(requester.getId(), receiver.getId());
        boolean backward = friendRequestRepository.existsRequestBetween(receiver.getId(), requester.getId());

        // then
        assertThat(forward).isTrue();
        assertThat(backward).isFalse();
    }
}
